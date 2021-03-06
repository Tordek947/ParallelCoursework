package ua.kpi.iasa.parallel.course;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import ua.kpi.iasa.parallel.course.methods.DiffeqCalculationMethod;
import ua.kpi.iasa.parallel.course.methods.impl.ExplicitDiffeqCalculationMethod;

@Configuration
@ComponentScan("ua.kpi.iasa.parallel.course")
public class SpringConfig {
	
	@Autowired
	ExplicitDiffeqCalculationMethod explicitDiffeqCalculationMethod;

	@Bean("conditionImageResource")
	public InputStream conditionImageResource() {
		return getClass().getResourceAsStream("/images/condition.png");
	}

	@Bean("preciseSolutionImageResource")
	public InputStream preciseSolutionImageResource() {
		return getClass().getResourceAsStream("/images/precise_solution.png");
	}
	
	@Bean("diffeqCalculationMethods")
	public List<DiffeqCalculationMethod> diffeqCalculationMethods() {
		return Arrays.asList(explicitDiffeqCalculationMethod);
	}
	
}
