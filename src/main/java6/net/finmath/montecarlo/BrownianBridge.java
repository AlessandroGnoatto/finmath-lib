/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 23.11.2013
 */

package net.finmath.montecarlo;

import java.util.Arrays;

import net.finmath.stochastic.RandomVariableInterface;
import net.finmath.time.TimeDiscretizationInterface;

/**
 * This class implements a Brownian bridge, i.e., samples of realizations of a Brownian motion
 * conditional to a given start and end value.
 * 
 * <p>
 * A Brownian bridge is a conditional Brownian motion, i.e. for given random variables
 * <i>X</i> and  <i>Y</i> the Brownian bridge is
 * <br>
 * <i>(W(t) | W(s) = X , W(T) = Y)</i>,
 * <br>
 * where <i>W</i> is a Brownian motion and <i>s &le; t &le; T</i>.
 * </p>
 * <p>
 * The samples of the Brownian bridge are generated by a Brownian motion which will be used to fill the gap between start and end.
 * It is important that this Browninan motion is independent from the one which generated start and end, i.e. here: it should have a different seed.
 * </p>
 * <p>
 * The class implements the {@code BrownianMotionInterface}, i.e., it only provides the increments
 * of the Brownian bridge (however, in most application, like refinement of an Euler-scheme, this is
 * exactly the desired object).
 * </p>
 * <p>
 * Note: The number of paths needs to be specified, because the start and the end point
 * may be not stochastic, i.e. it is not possible to infer this quantity
 * from the specified start and end.
 * </p>
 * 
 * @author Christian Fries
 * @date 24.11.2013
 */
public class BrownianBridge implements BrownianMotionInterface {

	private final TimeDiscretizationInterface						timeDiscretization;

	private final int	numberOfFactors;
	private final int	numberOfPaths;
	private final int	seed;

	private RandomVariableInterface[] start;
	private RandomVariableInterface[] end;

    private AbstractRandomVariableFactory randomVariableFactory = new RandomVariableFactory();

	private transient RandomVariableInterface[][]	brownianIncrements;	

	/**
	 * Construct a Brownian bridge, bridging from a given start to a given end.
	 * 
	 * @param timeDiscretization The time discretization used for the Brownian increments.
	 * @param numberOfPaths Number of paths to simulate.
	 * @param seed The seed of the random number generator.
	 * @param start Start value of the Brownian bridge.
	 * @param end End value of the Brownian bridge.
	 */
	public BrownianBridge(TimeDiscretizationInterface timeDiscretization, int numberOfPaths, int seed, RandomVariableInterface[] start, RandomVariableInterface[] end) {
		super();
		this.timeDiscretization = timeDiscretization;
		this.numberOfFactors = start.length;
		this.numberOfPaths = numberOfPaths;
		this.seed = seed;
		this.start = start;
		this.end = end;
	}

	/**
	 * Construct a Brownian bridge, bridging from a given start to a given end.
	 * 
	 * @param timeDiscretization The time discretization used for the Brownian increments.
	 * @param numberOfPaths Number of paths to simulate.
	 * @param seed The seed of the random number generator.
	 * @param start Start value of the Brownian bridge.
	 * @param end End value of the Brownian bridge.
	 */
	public BrownianBridge(TimeDiscretizationInterface timeDiscretization, int numberOfPaths, int seed, RandomVariableInterface start, RandomVariableInterface end) {
		this(timeDiscretization, numberOfPaths, seed, new RandomVariableInterface[] {start}, new RandomVariableInterface[] {end});
	}

	/* (non-Javadoc)
	 * @see net.finmath.montecarlo.BrownianMotionInterface#getBrownianIncrement(int, int)
	 */
	@Override
	public RandomVariableInterface getBrownianIncrement(int timeIndex, int factor) {
		// Thread safe lazy initialization
		synchronized(this) {
			if(brownianIncrements == null) doGenerateBrownianMotion();
		}

		/*
		 *  For performance reasons we return directly the stored data (no defensive copy).
		 *  We return an immutable object to ensure that the receiver does not alter the data.
		 */
		return brownianIncrements[timeIndex][factor];
	}

	/**
	 * Lazy initialization of brownianIncrement. Synchronized to ensure thread safety of lazy init.
	 */
	private void doGenerateBrownianMotion() {
		if(brownianIncrements != null) return;	// Nothing to do
		
		BrownianMotion generator = new BrownianMotion(timeDiscretization, numberOfFactors, numberOfPaths, seed);

		// Allocate memory
		brownianIncrements = new RandomVariableInterface[generator.getTimeDiscretization().getNumberOfTimeSteps()][generator.getNumberOfFactors()];

		double endTime 		= getTimeDiscretization().getTime(getTimeDiscretization().getNumberOfTimeSteps());
		for(int factor=0; factor<generator.getNumberOfFactors(); factor++) {
			// The end point
			RandomVariableInterface endOfFactor		= end[factor];
			// Initialized the bridge to the start point
			RandomVariableInterface brownianBridge	= start[factor];
			for(int timeIndex=0; timeIndex<getTimeDiscretization().getNumberOfTimeSteps(); timeIndex++) {
				double currentTime	= getTimeDiscretization().getTime(timeIndex);
				double nextTime		= getTimeDiscretization().getTime(timeIndex+1);
				double alpha		= (nextTime-currentTime)/(endTime-currentTime);

				// Calculate the next point using the "scheme" of the Brownian bridge
				RandomVariableInterface nextRealization = brownianBridge.mult(1.0-alpha).add(endOfFactor.mult(alpha)).add(generator.getBrownianIncrement(timeIndex, factor).mult(Math.sqrt(1-alpha)));
				
				// Store the increment
				brownianIncrements[timeIndex][factor] = nextRealization.sub(brownianBridge);
				
				// Update the bridge to the current point
				brownianBridge = nextRealization;
			}
		}
	}

	/* (non-Javadoc)
	 * @see net.finmath.montecarlo.BrownianMotionInterface#getTimeDiscretization()
	 */
	@Override
	public TimeDiscretizationInterface getTimeDiscretization() {
		return timeDiscretization;
	}

	/* (non-Javadoc)
	 * @see net.finmath.montecarlo.BrownianMotionInterface#getNumberOfFactors()
	 */
	@Override
	public int getNumberOfFactors() {
		return numberOfFactors;
	}

	/* (non-Javadoc)
	 * @see net.finmath.montecarlo.BrownianMotionInterface#getNumberOfPaths()
	 */
	@Override
	public int getNumberOfPaths() {
		return numberOfPaths;
	}

    @Override
    public RandomVariableInterface getRandomVariableForConstant(double value) {
        return randomVariableFactory.createRandomVariable(value);
    }

	/* (non-Javadoc)
	 * @see net.finmath.montecarlo.BrownianMotionInterface#getCloneWithModifiedSeed(int)
	 */
	@Override
	public BrownianMotionInterface getCloneWithModifiedSeed(int seed) {
		return new BrownianBridge(timeDiscretization, numberOfPaths, seed, start, end);
	}

	/* (non-Javadoc)
	 * @see net.finmath.montecarlo.BrownianMotionInterface#getCloneWithModifiedTimeDiscretization(net.finmath.time.TimeDiscretizationInterface)
	 */
	@Override
	public BrownianMotionInterface getCloneWithModifiedTimeDiscretization(TimeDiscretizationInterface newTimeDiscretization) {
		return new BrownianBridge(newTimeDiscretization, getNumberOfFactors(), seed, start, end);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "BrownianBridge [timeDiscretization=" + timeDiscretization
				+ ", numberOfFactors=" + numberOfFactors + ", numberOfPaths="
				+ numberOfPaths + ", seed=" + seed + ", start="
				+ Arrays.toString(start) + ", end=" + Arrays.toString(end)
				+ "]";
	}

}
