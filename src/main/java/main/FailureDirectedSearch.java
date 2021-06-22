/*
@author Arthur Godet <arth.godet@gmail.com>
@since 05/10/2020
*/

package main;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.PriorityQueue;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.loop.monitors.IMonitorContradiction;
import org.chocosolver.solver.search.loop.monitors.IMonitorDownBranch;
import org.chocosolver.solver.search.strategy.assignments.DecisionOperator;
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory;
import org.chocosolver.solver.search.strategy.decision.Decision;
import org.chocosolver.solver.search.strategy.decision.IntDecision;
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy;
import org.chocosolver.solver.variables.IntVar;

public class FailureDirectedSearch extends AbstractStrategy<IntVar> implements IMonitorDownBranch, IMonitorContradiction {
    public final static DecisionOperator<IntVar> OP = DecisionOperatorFactory.makeIntReverseSplit(); // operator to make decision of the format : var >= value
    public final static DecisionOperator<IntVar> OP2 = DecisionOperatorFactory.makeIntSplit(); // operator to make decision of the format : var < value
    public final static double ALPHA = 0.99;

    private final double decayFactor;
    private final HashMap<IntVar, VarRating> varRatings;
    private final HashMap<Long, double[]> avgRating;
    private boolean hasFailed;
    private double before;

    public FailureDirectedSearch(IntVar[] starts) {
        this(starts, ALPHA);
    }

    public FailureDirectedSearch(IntVar[] starts, double decayFactor) {
        super(starts);
        this.decayFactor = decayFactor;
        varRatings = new HashMap<>();
        avgRating = new HashMap<>();

        init();
    }

    @Override
    public boolean init() {
        varRatings.clear();
        for(int i = 0; i < vars.length; i++) {
            varRatings.put(vars[i], new VarRating(vars[i]));
        }
        avgRating.clear();
        return true;
    }

    @Override
    public Decision<IntVar> computeDecision(IntVar variable) {
        if (variable == null || variable.isInstantiated()) {
            return null;
        }
        Rating bestRating = varRatings.get(variable).stackBestRating();
        return variable.getModel().getSolver().getDecisionPath().makeIntDecision(
            variable,
            bestRating.getPositiveRating() <= bestRating.getNegativeRating() ? OP : OP2,
            bestRating.value
        );
    }

    @Override
    public Decision<IntVar> getDecision() {
        IntVar variable = null;
        double bestRating = 0.0;
        for(IntVar var : varRatings.keySet()) {
            if(!var.isInstantiated()) {
                varRatings.get(var).unstack();
                double rating = varRatings.get(var).getBestRating().getRating();
                if(variable == null || rating < bestRating) {
                    variable = var;
                    bestRating = rating;
                }
            }
        }
        return computeDecision(variable);
    }

    private double searchSpaceSize() {
        double res = 1.0;
        for(int i = 0; i < vars.length; i++) {
            res *= vars[i].getDomainSize();
        }
        return res;
    }

    @Override
    public void beforeDownBranch(boolean left) {
        hasFailed = false;
        before = searchSpaceSize();
    }

    @Override
    public void afterDownBranch(boolean left) {
        IntDecision decision = (IntDecision) vars[0].getModel().getSolver().getDecisionPath().getLastDecision();
        double localRating;
        if(hasFailed) {
            localRating = 0.0;
        } else {
            localRating = 1.0 + searchSpaceSize() / before;
        }
        Rating rating = varRatings.get(decision.getDecisionVariable()).getRating(decision.getDecisionValue());
        boolean positive = decision.getDecOp().equals(OP);
        long d = getVariables()[0].getModel().getSolver().getDecisionCount();
        double[] array = avgRating.computeIfAbsent(d, k -> new double[]{1.0,1.0});
        double avg = (array[1] == 0.0 ? 1.0 : array[0] / array[1]);
        double newRating = decayFactor * (positive ? rating.getPositiveRating() : rating.getNegativeRating()) + (1.0 - decayFactor) * localRating / avg;

        if(positive) {
            rating.setPositiveRating(newRating);
        } else {
            rating.setNegativeRating(newRating);
        }
        array[0] += newRating;
        array[1]++;

        for(IntVar var : varRatings.keySet()) {
            varRatings.get(var).stack();
        }
    }

    @Override
    public void onContradiction(ContradictionException cex) {
        hasFailed = true;
    }

    private static class VarRating {
        final IntVar var;
        final HashMap<Integer, Rating> ratings;
        final PriorityQueue<Rating> unchecked;
        final ArrayList<Rating> resolved; // TODO : initially, should be a stack but harder to make it work

        public VarRating(IntVar var) {
            this.var = var;
            unchecked = new PriorityQueue<>();
            resolved = new ArrayList<>();
            ratings = new HashMap<>();
            for(int value = var.nextValue(var.getLB()); value <= var.getUB(); value = var.nextValue(value)) {
                Rating rating = new Rating(value);
                ratings.put(value, rating);
                unchecked.add(rating);
            }
        }

        public void stack() {
            while(!unchecked.isEmpty() && (var.getLB() > unchecked.peek().value || var.getUB() < unchecked.peek().value)) {
                resolved.add(unchecked.poll());
            }
        }

        public void unstack() {
            for(int k = 0; k < resolved.size(); k++) {
                if(var.getLB() <= resolved.get(k).value && var.getUB() >= resolved.get(k).value) {
                    unchecked.add(resolved.remove(k));
                    k--;
                }
            }
        }

        public Rating getRating(int value) {
            return ratings.get(value);
        }

        public Rating getBestRating() {
            return unchecked.peek();
        }

        public Rating stackBestRating() {
            Rating rating = unchecked.poll();
            resolved.add(rating);
            return rating;
        }

        @Override
        public String toString() {
            return "VarRatings["+var+","+ratings.values()+"]";
        }
    }

    private static class Rating implements Comparable<Rating> {
        final int value;
        final double[] ratings;

        public Rating(int value) {
            this.value = value;
            ratings = new double[]{1.0,1.0};
        }

        public void init() {
            ratings[0] = 1.0;
            ratings[1] = 1.0;
        }

        public double getPositiveRating() {
            return ratings[0];
        }

        public double getNegativeRating() {
            return ratings[1];
        }

        public double getRating() {
            return ratings[0] + ratings[1];
        }

        public void setPositiveRating(double rating) {
            ratings[0] = rating;
        }

        public void setNegativeRating(double rating) {
            ratings[1] = rating;
        }

        @Override
        public int compareTo(Rating o) {
            return Double.compare(this.getRating(), o.getRating());
        }

        @Override
        public String toString() {
            return "Rating("+value+","+ Arrays.toString(ratings)+")";
        }
    }
}
