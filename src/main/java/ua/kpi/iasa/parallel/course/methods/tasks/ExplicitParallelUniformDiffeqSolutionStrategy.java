package ua.kpi.iasa.parallel.course.methods.tasks;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;

import org.jzy3d.maths.Range;
import org.springframework.stereotype.Component;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.concurrent.Task;
import ua.kpi.iasa.parallel.course.TaskFailedException;
import ua.kpi.iasa.parallel.course.data.AbstractUniformGrid;
import ua.kpi.iasa.parallel.course.data.GridValuePointer;
import ua.kpi.iasa.parallel.course.data.UniformGrid;
import ua.kpi.iasa.parallel.course.methods.ExplicitPointResolver;

@Component("explicitParallelDiffeqSolutionStrategy")
public class ExplicitParallelUniformDiffeqSolutionStrategy
		extends ExplicitUniformDiffeqSolutionStrategy {

	private final int minXStepsPerThread;
	private final int maxThreadCount;

	public ExplicitParallelUniformDiffeqSolutionStrategy() {
		this.minXStepsPerThread = 2;
		this.maxThreadCount = Runtime.getRuntime().availableProcessors();
	}

	@Override
	public Task<UniformGrid> makeDiffeqSolutionTask(Range xRange, Range tRange,
			int xSteps, int tSteps) {
		int calculatedThreadCount = xSteps/minXStepsPerThread;
		final int actualThreadCount = calculatedThreadCount > maxThreadCount ?
				maxThreadCount : calculatedThreadCount;
		if (actualThreadCount == 1) {
			return super.makeDiffeqSolutionTask(xRange, tRange, xSteps, tSteps);
		}
		return new Task<UniformGrid>() {

			@Override
			protected UniformGrid call() throws Exception {
				Date startDate = logBefore("explicitParallelDiffeqSolutionStrategy");
				updateMessage(BUILDING_NODE_POINTS_FOR_PLOT_MESSAGE + "...");
				UniformGrid grid = new UniformGrid(xRange, tRange, xSteps, tSteps);
				ExecutorService executor = Executors.newFixedThreadPool(actualThreadCount);
				Semaphore[] semaphores = new Semaphore[(actualThreadCount-2)*2 + 2];
				for(int i = 0; i< semaphores.length; i++) {
					semaphores[i] = new Semaphore(1);
				}
				int semInd = 0;
				List<AbstractSubGridSolverRunnable> subGridSolvers = new ArrayList<>(actualThreadCount);
				final int xStepsPerThread = xSteps/actualThreadCount;
				if (isCancelled()) {
					return null;
				}

				int currentThreadFromXIndex = 0;
				subGridSolvers.add(new LeftSubGridSolverRunnable(grid.subXGrid(currentThreadFromXIndex, xStepsPerThread),
						semaphores[semInd], semaphores[semInd+1]));
				currentThreadFromXIndex += xStepsPerThread;
				for(int i = 1; i<actualThreadCount-1; i++) {
					subGridSolvers.add(new IntermediateSubGridSolverRunnable(
							grid.subXGrid(currentThreadFromXIndex, xStepsPerThread),
							semaphores[semInd], semaphores[semInd+1], semaphores[semInd+2], semaphores[semInd+3]));
					semInd+=2;
					currentThreadFromXIndex += xStepsPerThread;
				}
				RightSubGridSolverRunnable runner = new RightSubGridSolverRunnable(
						grid.subXGrid(currentThreadFromXIndex, xSteps - currentThreadFromXIndex),
						semaphores[semInd], semaphores[semInd+1]);
				runner.progressProperty().addListener((observable, oldV, newV)-> updateProgress(newV.doubleValue(), 1.));
				runner.setIsCancelled(this::isCancelled);
				if (isCancelled()) {
					return null;
				}
				
				subGridSolvers.stream().forEach(executor::execute);
				runner.run();
				if (isCancelled()) {
					executor.shutdownNow();
					return null;
				}
				updateMessage(BUILDING_PLOT_GRID_VALUES_MESSAGE + "...");
				executor.shutdown();
				try {
					executor.awaitTermination(2, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					System.err.println("executor interrupts execution!");
				}
				logAfter(startDate, grid.getGridNodeValues());
				if (isCancelled()) {
					return null;
				}
				checkBuildNodePointsAbility(grid);
				updateProgress(1., 1.);
				return grid;
			}

			private void checkBuildNodePointsAbility(UniformGrid grid) throws TaskFailedException {
				updateProgress(-1., 1.);
				updateMessage(BUILDING_NODE_POINTS_FOR_PLOT_MESSAGE + "...");
				if (grid.getXStepsCount() * grid.getTStepsCount() > PLOT_POINTS_LIST_MAX_SIZE) {
					throw new TaskFailedException(BUILDING_NODE_POINTS_FOR_PLOT_MESSAGE + " failed",
							"There are too many plot points to gather them to list");
				}
				grid.buildNodePoints();
			}
		};
	}

	private abstract class AbstractSubGridSolverRunnable implements Runnable {
		protected final AbstractUniformGrid grid;
		protected final ExplicitPointResolver resolver;

		public AbstractSubGridSolverRunnable(final AbstractUniformGrid grid) {
			this.grid = grid;
			final double dx = grid.getDx();
			final double dt = grid.getDt();
			resolver = new ExplicitPointResolver(dt, dx, mainParametersService.getAlpha());
		}

		protected double calculateValue(final GridValuePointer gridPointer) {
			return calculatePointValue(gridPointer, resolver);
		}

		protected void acquireSemaphore(Semaphore sem) {
			try {
				sem.acquire();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private class IntermediateSubGridSolverRunnable extends AbstractSubGridSolverRunnable {

		private final Semaphore leftSemaphore;
		private final Semaphore rightSemaphore;
		private final Semaphore neighbourLeftSemaphore;
		private final Semaphore neighbourRightSemaphore;

		public IntermediateSubGridSolverRunnable(AbstractUniformGrid grid, Semaphore neighbourLeftSemaphore,
				Semaphore leftSemaphore, Semaphore rightSemaphore, Semaphore neighbourRightSemaphore) {
			super(grid);
			this.leftSemaphore = leftSemaphore;
			this.rightSemaphore = rightSemaphore;
			this.neighbourLeftSemaphore = neighbourLeftSemaphore;
			this.neighbourRightSemaphore = neighbourRightSemaphore;
		}

		@Override
		public void run() {
			final GridValuePointer gridPointer = makeGridPointerFilledWithInitialCondidions(grid);

			while(gridPointer.canMakePositiveTStep()) {
				gridPointer.makePositiveTStepResettingX();
				gridPointer.makePositiveXStep();
				acquireSemaphore(neighbourLeftSemaphore);
				gridPointer.setCurrentValue(calculateValue(gridPointer));
				leftSemaphore.release();
				gridPointer.makePositiveXStep();
				while(gridPointer.canMakePositiveXStep()) {
					gridPointer.setCurrentValue(calculateValue(gridPointer));
					gridPointer.makePositiveXStep();
				}
				acquireSemaphore(neighbourRightSemaphore);
				gridPointer.setCurrentValue(calculateValue(gridPointer));
				rightSemaphore.release();

				if (!gridPointer.canMakePositiveTStep()) {
					break;
				}

				gridPointer.makePositiveTStep();
				acquireSemaphore(neighbourRightSemaphore);
				gridPointer.setCurrentValue(calculateValue(gridPointer));
				rightSemaphore.release();
				gridPointer.makeNegativeXStep();
				while(gridPointer.canMakeNegativeXStep()) {
					gridPointer.setCurrentValue(calculateValue(gridPointer));
					gridPointer.makeNegativeXStep();
				}
				acquireSemaphore(neighbourLeftSemaphore);
				gridPointer.setCurrentValue(calculateValue(gridPointer));
				leftSemaphore.release();				
			}
		}
	}

	private class LeftSubGridSolverRunnable extends AbstractSubGridSolverRunnable {

		private final Semaphore rightSemaphore;
		private final Semaphore neighbourRightSemaphore;
		private final DoubleUnaryOperator leftEdgeCondition;

		public LeftSubGridSolverRunnable(AbstractUniformGrid abstractUniformGrid, Semaphore rightSemaphore,
				Semaphore neighbourRightSemaphore) {
			super(abstractUniformGrid);
			this.rightSemaphore = rightSemaphore;
			this.neighbourRightSemaphore = neighbourRightSemaphore;
			this.leftEdgeCondition = conditionService.getLeftEdgeCondition();
		}

		@Override
		public void run() {
			final GridValuePointer gridPointer = makeGridPointerFilledWithInitialCondidions(grid);

			while(gridPointer.canMakePositiveTStep()) {
				gridPointer.makePositiveTStepResettingX();
				gridPointer.makePositiveXStep();
				gridPointer.setCurrentValueApplyingToT(leftEdgeCondition);
				gridPointer.makePositiveXStep();
				while(gridPointer.canMakePositiveXStep()) {
					gridPointer.setCurrentValue(calculateValue(gridPointer));
					gridPointer.makePositiveXStep();
				}
				acquireSemaphore(neighbourRightSemaphore);
				gridPointer.setCurrentValue(calculateValue(gridPointer));
				rightSemaphore.release();

				if (!gridPointer.canMakePositiveTStep()) {
					break;
				}

				gridPointer.makePositiveTStep();
				acquireSemaphore(neighbourRightSemaphore);
				gridPointer.setCurrentValue(calculateValue(gridPointer));
				rightSemaphore.release();
				gridPointer.makeNegativeXStep();
				while(gridPointer.canMakeNegativeXStep()) {
					gridPointer.setCurrentValue(calculateValue(gridPointer));
					gridPointer.makeNegativeXStep();
				}
				gridPointer.setCurrentValueApplyingToT(leftEdgeCondition);			
			}
		}
	}

	private class RightSubGridSolverRunnable extends AbstractSubGridSolverRunnable {

//		private final Semaphore leftSemaphore;
//		private final Semaphore neighbourLeftSemaphore;
		private final DoubleUnaryOperator rightEdgeCondition;
		private final Task<Void> innerTask;
		private Supplier<Boolean> isCancelledSupplier;

		public RightSubGridSolverRunnable(final AbstractUniformGrid abstractUniformGrid,
				final Semaphore neighbourLeftSemaphore, final Semaphore leftSemaphore) {
			super(abstractUniformGrid);
//			this.leftSemaphore = leftSemaphore;
//			this.neighbourLeftSemaphore = neighbourLeftSemaphore;
			this.rightEdgeCondition = conditionService.getRightEdgeCondition();
			
			innerTask = new Task<Void>() {

				@Override
				protected Void call() throws Exception {
					long totalWork = grid.getTStepsCount();
					updateProgress(0, totalWork);
					final int iterCountPerPercent = (int) (totalWork/100);
					final GridValuePointer gridPointer = makeGridPointerFilledWithInitialCondidions(grid);
					if (isOuterTaskCancelled()) {
						return null;
					}
					long currentWork = 2;
					updateProgress(currentWork, totalWork);
					while(gridPointer.canMakePositiveTStep()) {
						if (isOuterTaskCancelled()) {
							return null;
						}
						gridPointer.makePositiveTStepResettingX();
						gridPointer.makePositiveXStep();
						acquireSemaphore(neighbourLeftSemaphore);
						gridPointer.setCurrentValue(calculateValue(gridPointer));
						leftSemaphore.release();
						gridPointer.makePositiveXStep();
						while(gridPointer.canMakePositiveXStep()) {
							gridPointer.setCurrentValue(calculateValue(gridPointer));
							gridPointer.makePositiveXStep();
						}
						gridPointer.setCurrentValueApplyingToT(rightEdgeCondition);
						if (++currentWork % iterCountPerPercent == 0) {
							updateProgress(currentWork, totalWork);	
						}
						if (!gridPointer.canMakePositiveTStep()) {
							break;
						}

						if (isOuterTaskCancelled()) {
							return null;
						}
						gridPointer.makePositiveTStep();
						gridPointer.setCurrentValueApplyingToT(rightEdgeCondition);
						gridPointer.makeNegativeXStep();
						while(gridPointer.canMakeNegativeXStep()) {
							gridPointer.setCurrentValue(calculateValue(gridPointer));
							gridPointer.makeNegativeXStep();
						}
						acquireSemaphore(neighbourLeftSemaphore);
						gridPointer.setCurrentValue(calculateValue(gridPointer));
						leftSemaphore.release();
						if (++currentWork % iterCountPerPercent == 0) {
							updateProgress(currentWork, totalWork);	
						}			
					}
					return null;
				}
				
			};
			
		}

		public void setIsCancelled(Supplier<Boolean> isCancelledSupplier) {
			this.isCancelledSupplier = isCancelledSupplier;
		}
		
		private boolean isOuterTaskCancelled() {
			return isCancelledSupplier.get();
		}

		@Override
		public void run() {
			innerTask.run();
		}
		
		public ReadOnlyDoubleProperty progressProperty() {
			return innerTask.progressProperty();
		}
	}
}

