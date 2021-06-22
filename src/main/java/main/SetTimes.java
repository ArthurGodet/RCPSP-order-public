/*
@author Arthur Godet <arth.godet@gmail.com>
@since 01/06/2020
*/

package main;

import java.util.Arrays;
import org.chocosolver.memory.IStateInt;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.strategy.selectors.variables.VariableSelector;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Task;
import org.chocosolver.util.ESat;

/**
 * Search for scheduling problems described in the following paper :
 * Godard, D., Laborie, P., Nuijten, W.: Randomized large neighborhood search for cumulative scheduling. In: Biundo, S., Myers, K.L., Rajan, K. (eds.) Proceedings of the Fifteenth International Conference on Automated Planning and Scheduling(ICAPS 2005), June 5-10 2005, Monterey, California, USA. pp. 81–89. AAAI (2005),http://www.aaai.org/Library/ICAPS/2005/icaps05-009.php
 *
 * Beware that the search is not complete in general, as stated in the paper.
 *
 * @author Arthur Godet <arth.godet@gmail.com>
 * @since 26/05/2020
 */
public class SetTimes extends Propagator<IntVar> implements VariableSelector<IntVar> {
    private final Task[] tasks;
    private final IntVar[] starts;
    private final int notMarked;
    private final IStateInt[] lastStartTime;

    public SetTimes(Task[] tasks) {
        super(Arrays.stream(tasks).map(Task::getStart).toArray(IntVar[]::new), PropagatorPriority.VERY_SLOW, false);
        this.tasks = tasks;
        notMarked = Arrays.stream(tasks).mapToInt(t -> t.getStart().getLB()).min().getAsInt() - 1;
        lastStartTime = new IStateInt[tasks.length];
        Model model = tasks[0].getStart().getModel();
        for(int i = 0; i < tasks.length; i++) {
            lastStartTime[i] = model.getEnvironment().makeInt(notMarked);
        }
        starts = Arrays.stream(tasks).map(Task::getStart).toArray(IntVar[]::new);
    }

    private IntVar selectNextVariable(boolean mark) {
        int idx = -1;
        for(int i = 0; i < tasks.length; i++) {
            IntVar start = tasks[i].getStart();
            if(!start.isInstantiated()) {
                if(lastStartTime[i].get() != notMarked && lastStartTime[i].get() < start.getLB()) {
                    lastStartTime[i].set(notMarked);
                }
                if(lastStartTime[i].get() == notMarked) {
                    if(
                        idx == -1
                            || start.getLB() < tasks[idx].getStart().getLB()
                            || start.getLB() == tasks[idx].getStart().getLB() && tasks[i].getEnd().getLB() < tasks[idx].getEnd().getLB()
                    ) {
                        idx = i;
                    }
                }
            }
        }
        if(idx != -1) {
            if(mark) {
                lastStartTime[idx].set(starts[idx].getLB() + 1); // + 1 because start is updated on refuted branch
            }
            return tasks[idx].getStart();
        } else {
            return null;
        }
    }

    @Override
    public IntVar getVariable(IntVar[] variables) {
        return selectNextVariable(true);
    }

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        if(isCompletelyInstantiated()) {
            setPassive();
        } else if(selectNextVariable(false) == null) {
            fails();
        }
    }

    @Override
    public ESat isEntailed() {
        return ESat.TRUE;
    }
}
