package ua.kpi.iasa.parallel.course.services;

import org.jzy3d.maths.Range;
import org.springframework.stereotype.Service;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;

@Service
public class MainParametersService {
	private final DoubleProperty xMinProperty;
	private final DoubleProperty xMaxProperty;
	private final IntegerProperty xStepsProperty;
	private final DoubleProperty tMinProperty;
	private final DoubleProperty tMaxProperty;
	private final IntegerProperty tStepsProperty;
	private final double alpha = 1;
	
	
	public MainParametersService() {
		xMinProperty = new SimpleDoubleProperty(-10);
		xMaxProperty = new SimpleDoubleProperty(10);
		xStepsProperty = new SimpleIntegerProperty(10);
		tMinProperty = new SimpleDoubleProperty(-10);
		tMaxProperty = new SimpleDoubleProperty(-2);
		tStepsProperty = new SimpleIntegerProperty(10);
	}
	
	public double getAlpha() {
		return alpha;
	}
	
	public DoubleProperty xMinProperty() {
		return xMinProperty;
	}

	public DoubleProperty xMaxProperty() {
		return xMaxProperty;
	}

	public IntegerProperty xStepsProperty() {
		return xStepsProperty;
	}

	public DoubleProperty tMinProperty() {
		return tMinProperty;
	}

	public DoubleProperty tMaxProperty() {
		return tMaxProperty;
	}

	public IntegerProperty tStepsProperty() {
		return tStepsProperty;
	}

	public Range getXRange() {
		return new Range((float)xMinProperty.get(), (float)xMaxProperty.get());
	}
	
	public Range getTRange() {
		return new Range((float)tMinProperty.get(), (float)tMaxProperty.get());
	}

	public int getXSteps() {
		return xStepsProperty.get();
	}

	public int getTSteps() {
		return tStepsProperty.get();
	}

	public void setTSteps(int tSteps) {
		tStepsProperty.set(tSteps);
	}

		
}
