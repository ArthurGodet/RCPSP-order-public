/*
@author Arthur Godet <arth.godet@gmail.com>
@since 01/07/2019
*/
package main;

import alldifferentprec.PropAllDiffPrec;
import data.Activity;
import data.Factory;
import data.InstanceSP;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import leftShifted.PropOrderLeftShifted;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.nary.alldifferent.PropAllDiffAC;
import org.chocosolver.solver.constraints.nary.alldifferent.PropAllDiffBC;
import org.chocosolver.solver.constraints.nary.alldifferent.PropAllDiffInst;
import org.chocosolver.solver.constraints.nary.channeling.PropInverseChannelAC;
import org.chocosolver.solver.search.limits.FailCounter;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMin;
import org.chocosolver.solver.search.strategy.selectors.variables.InputOrder;
import org.chocosolver.solver.search.strategy.selectors.variables.Smallest;
import org.chocosolver.solver.search.strategy.strategy.IntStrategy;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Task;

public class RCPSPModel {
    private final Model model;
    private final Task[] tasks;
    private final IntVar[] order;
    private IntVar[] indexes;
    private final IntVar[] starts;
    private final IntVar makespan;
    private BoolVar[][] precedence;

    public RCPSPModel(InstanceSP instance, ConfigurationSearch configuration) {
        this.model = new Model();
        tasks = new Task[instance.getActivities().size()];
        starts = new IntVar[instance.getActivities().size()];
        int hor = instance.getHorizon();
        for(int k = 0; k<tasks.length; k++) {
            Activity activity = instance.getActivities().get(k);
            int d = activity.getDuration();
            starts[k] = model.intVar("start[" + activity.getID() + "]", 0, hor - d);
            tasks[k] = new Task(starts[k], d);
        }

        // precedence relations
        for(Activity j : instance.getActivities()) {
            int idJ = j.getID();
            for(int s : j.getSuccessors()) {
                model.arithm(tasks[idJ].getEnd(), "<=", tasks[s].getStart()).post();
            }
        }

        // resource management
        ArrayList<Task> taskVars = new ArrayList<>();
        ArrayList<IntVar> heightVars = new ArrayList<>();
        for(int i = 0; i < instance.getResourcesAvailabilities().length; i++) {
            taskVars.clear();
            heightVars.clear();
            for(int k = 0; k < tasks.length; k++) {
                int resourceConsumption = instance.getActivities().get(k).getResourceConsumption()[i];
                if(resourceConsumption > 0) {
                    IntVar height = model.intVar(resourceConsumption);
                    heightVars.add(height);
                    taskVars.add(tasks[k]);
                }
            }
            if(taskVars.size() > 0) {
                model.cumulative(taskVars.toArray(new Task[0]), heightVars.toArray(new IntVar[0]), model.intVar(instance.getResourceAvailability(i))).post();
            }
        }

        makespan = model.intVar("makespan", 0, hor);
        model.max(makespan, Arrays.stream(tasks).map(Task::getEnd).toArray(IntVar[]::new)).post();
        model.setObjective(false, makespan);

        if(
            configuration.equals(ConfigurationSearch.ALL_DIFF_PREC)
                || configuration.equals(ConfigurationSearch.ALL_DIFF_PREC_IMP)
                || configuration.equals(ConfigurationSearch.ALL_DIFF_PREC_DEC)
        ) {
            order = model.intVarArray("order", starts.length, 0, starts.length-1);
            int[][] heights = new int[instance.getActivities().size()][];
            for(int i = 0; i < heights.length; i++) {
                heights[i] = instance.getActivities().get(i).getResourceConsumption();
            }
            PropOrderLeftShifted propOrderLeftShifted = new PropOrderLeftShifted(
                order,
                starts,
                Arrays.stream(tasks).mapToInt(t -> t.getDuration().getValue()).toArray(),
                heights,
                instance.getResourcesAvailabilities(),
                instance.getPredecessors()
            );
            IntStrategy orderSmallest = Search.intVarSearch(
                new InputOrder<>(model),
                var -> {
                    int id = var.getLB();
                    int val = starts[id].getLB();
                    for(int i = var.getLB(); i <= var.getUB(); i = var.nextValue(i)) {
                        if(val > starts[i].getLB()) {
                            id = i;
                            val = starts[i].getLB();
                        }
                    }
                    return id;
                },
                order
            );
            this.indexes = model.intVarArray("indexes", order.length, 0, order.length - 1);
            this.precedence = PropAllDiffPrec.buildPrecedenceVars(model, instance.getPredecessors(), instance.getSuccessors());
            boolean[][] prec = PropAllDiffPrec.buildPrecedence(instance.getPredecessors(), instance.getSuccessors());
            for(int i = 0; i < precedence.length; i++) {
                for(int j = 0; j < precedence.length; j++) {
                    if(i < j && !precedence[i][j].isInstantiated()) {
                        model.arithm(tasks[i].getStart(), "<=", tasks[j].getStart())
                             .reifyWith(precedence[i][j]);
                    }
                }
            }
            ArrayList<Propagator<IntVar>> list = new ArrayList<>();
            list.add(new PropInverseChannelAC(order, indexes, 0, 0));
            list.add(new PropAllDiffInst(order));
            list.add(new PropAllDiffAC(order, true));
            list.add(propOrderLeftShifted);
            list.add(new PropAllDiffInst(indexes));
            if(configuration.equals(ConfigurationSearch.ALL_DIFF_PREC_DEC)) {
                for(int i = 0; i < precedence.length; i++) {
                    for(int j = 0; j < precedence.length; j++) {
                        if(i != j && !precedence[i][j].isInstantiated()) {
                            model.ifThen(
                                precedence[i][j],
                                model.arithm(indexes[i], "<", indexes[j])
                            );
                        } else if(i != j) {
                            if(precedence[i][j].isInstantiatedTo(1)) {
                                model.arithm(indexes[i], "<", indexes[j]).post();
                            } else {
                                model.arithm(indexes[i], ">", indexes[j]).post();
                            }
                        }
                    }
                }
                list.add(new PropAllDiffBC(indexes));
            } else if(configuration.equals(ConfigurationSearch.ALL_DIFF_PREC)) {
                list.add(new PropAllDiffBC(indexes));
                list.add(new PropAllDiffPrec(indexes, prec, "BESSIERE"));
            } else {
                list.add(new PropAllDiffAC(indexes, true));
                list.add(new PropAllDiffPrec(indexes, prec, "GODET"));
            }

            model.getSolver().setSearch(orderSmallest);

            model.post(
                new Constraint(
                    "ORDER_CONSTRAINT",
                    list.toArray(new Propagator[0])
                )
            );
        } else {
            order = null;
            declareSearch(configuration);
        }

        model.getSolver().setSearch(
            model.getSolver().getSearch(),
            Search.inputOrderLBSearch(makespan)
        );
    }

    private void declareSearch(ConfigurationSearch configuration) {
        if (configuration.equals(ConfigurationSearch.SMALLEST)) {
            model.getSolver().setSearch(Search.intVarSearch(new Smallest(), new IntDomainMin(), starts));
        } else if (configuration.equals(ConfigurationSearch.SET_TIMES)) {
            SetTimes setTimes = new SetTimes(tasks);
            model.post(new Constraint("SET_TIMES", setTimes));
            model.getSolver().setSearch(Search.intVarSearch(setTimes, new IntDomainMin(), starts));
        } else if(configuration.equals(ConfigurationSearch.FDS)) {
            FailureDirectedSearch fds = new FailureDirectedSearch(starts);
            model.getSolver().plugMonitor(fds);
            model.getSolver().setSearch(fds);
            model.getSolver().setGeometricalRestart(100, 1.15, new FailCounter(model, Integer.MAX_VALUE), Integer.MAX_VALUE);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public Model getModel() {
        return model;
    }

    public static String toString(Solver solver, boolean finalStats) {
        return (finalStats ?
                solver.getMeasures().getTimeToBestSolutionInNanoSeconds() :
                solver.getMeasures().getTimeCountInNanoSeconds()
        ) / 1000000 + ";"
            + solver.getBestSolutionValue().intValue() + ";"
            + solver.getNodeCount() + ";"
            + solver.getBackTrackCount() + ";"
            + solver.getFailCount() + ";";
    }

    public static void main(String[] args) {
        ConfigurationSearch configuration = ConfigurationSearch.valueOf(args[0]);
        long timeLimitInMilliseconds = Long.parseLong(args[1]) * 60000;
        InstanceSP instance = Factory.fromFile(args[2], InstanceSP.class);
        RCPSPModel rcpspModel = new RCPSPModel(instance, configuration);
        Solver solver = rcpspModel.getModel().getSolver();

        solver.limitTime(timeLimitInMilliseconds);
        while(solver.solve()) {
            System.out.println(toString(solver, false));
        }
        System.out.println(
            instance.getName() + ";"
                + solver.getTimeCountInNanoSeconds() / 1000000 + ";"
                + toString(solver, true)
        );
    }
}
