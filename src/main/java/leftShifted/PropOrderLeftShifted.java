/*
@author Arthur Godet <arth.godet@gmail.com>
@since 02/12/2020
*/

package leftShifted;

import java.util.Arrays;
import org.chocosolver.memory.IStateBool;
import org.chocosolver.memory.IStateInt;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.events.IntEventType;
import org.chocosolver.util.ESat;
import org.chocosolver.util.tools.ArrayUtils;

/**
 * Propagator for the LeftShifted constraint introduced in the following thesis :
 * TODO: add the thesis citation when it is fixed (and/or the CP paper if accepted)
 *
 * @author Arthur Godet <arth.godet@gmail.com>
 */
public class PropOrderLeftShifted extends Propagator<IntVar> {
    protected final IntVar[] order;
    protected final IntVar[] starts;
    protected final int[] duration;
    protected final int[][] heights;
    protected final int[] capacities;
    protected final int[][] predecessors;

    protected final int n;
    protected final int[] slb, sub;
    protected final int[] elb, eub;
    protected final int[] dlb;
    protected final int[] hlb;
    protected final Profile[] profiles;
    protected final int min;

    protected final IStateInt idxCurrentOrder;
    protected final IStateBool[] isOrdered;

    public PropOrderLeftShifted(IntVar[] order, IntVar[] starts, int[] duration, int[][] heights, int[] capacities, int[][] predecessors) {
        super(ArrayUtils.append(order, starts), PropagatorPriority.QUADRATIC, false);
        this.order = order;
        this.starts = starts;
        this.duration = duration;
        this.heights = heights;
        this.capacities = capacities;
        this.predecessors = predecessors;

        n = order.length;
        slb = new int[n];
        sub = new int[n];
        elb = new int[n];
        eub = new int[n];
        dlb = new int[n];
        hlb = new int[n];
        profiles = new Profile[capacities.length];
        for(int c = 0; c < capacities.length; c++) {
            profiles[c] = new Profile(n);
        }

        this.isOrdered = new IStateBool[order.length];
        for(int i = 0; i < isOrdered.length; i++) {
            this.isOrdered[i] = getModel().getEnvironment().makeBool(false);
        }

        this.idxCurrentOrder = getModel().getEnvironment().makeInt(0);
        min = Arrays.stream(starts).mapToInt(IntVar::getLB).min().getAsInt();
    }

    @Override
    public int getPropagationConditions(int vIdx) {
        if(vIdx < order.length) {
            return IntEventType.instantiation();
        } else {
            return IntEventType.all();
        }
    }

    protected void updateIdxCurrentOrder() {
        int idx = idxCurrentOrder.get();
        while(idx < order.length && order[idx].isInstantiated() && isOrdered[order[idx].getValue()].get()) {
            idx++;
        }
        idxCurrentOrder.set(idx);
        if(idxCurrentOrder.get() == order.length) {
            setPassive();
        }
    }

    private void buildSchedule() {
        int idx = idxCurrentOrder.get();
        for(int c = 0; c < capacities.length; c++) {
            int nb = 0;
            for(int i = 0; i < idx; i++) {
                int value = order[i].getValue();
                if(heights[value][c] > 0) {
                    slb[nb] = starts[value].getLB();
                    sub[nb] = starts[value].getUB();
                    elb[nb] = slb[nb] + duration[value];
                    eub[nb] = sub[nb] + duration[value];
                    dlb[nb] = duration[value];
                    hlb[nb++] = heights[value][c];
                }
            }
            profiles[c].buildProfile(nb, sub, elb, hlb);
        }
    }

    private boolean canBePlacedAt(int v, int c, int j) {
        int idxRect = j;
        int s = profiles[c].getStartRectangle(idxRect);
        int d = duration[v];
        while(
            s + d > profiles[c].getEndRectangle(idxRect)
            && profiles[c].getHeightRectangle(idxRect) + heights[v][c] <= capacities[c]
        ) {
            d -= profiles[c].getEndRectangle(idxRect) - profiles[c].getStartRectangle(idxRect);
            idxRect++;
            s = profiles[c].getStartRectangle(idxRect);
        }
        return s + d <= profiles[c].getEndRectangle(idxRect)
            && profiles[c].getHeightRectangle(idxRect) + heights[v][c] <= capacities[c];
    }

    private int minAccValue(int v) {
        int m = min;
        for(int k = 0; k < predecessors[v].length; k++) {
            boolean alreadyPlaced = false;
            for(int i = 0; i < idxCurrentOrder.get() && !alreadyPlaced; i++) {
                alreadyPlaced = predecessors[v][k] == order[i].getValue();
            }
            if(alreadyPlaced) {
                m = Math.max(m, starts[predecessors[v][k]].getValue() + duration[predecessors[v][k]]);
            }
        }
        int formerM;
        do {
            formerM = m;
            for(int c = 0; c < capacities.length; c++) {
                boolean found = false;
                for(int j = profiles[c].find(m); j < profiles[c].size(); j++) {
                    if(canBePlacedAt(v,c,j)) {
                        m = Math.max(m, profiles[c].getStartRectangle(j));
                        found = true;
                        break;
                    }
                }
                if(!found) {
                    int j = profiles[c].size();
                    m = Math.max(m, profiles[c].getStartRectangle(j));
                }
            }
        } while(formerM != m);
        return m;
    }

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        do {
            updateIdxCurrentOrder();
            if(idxCurrentOrder.get() == order.length) {
                return;
            }
            buildSchedule();
            int idx = idxCurrentOrder.get();
            if(idx > 0) {
                for(int v = order[idx].getLB(); v <= order[idx].getUB(); v = order[idx].nextValue(v)) {
                    if(starts[v].getLB() > minAccValue(v)) {
                        order[idx].removeValue(v, this);
                    }
                }
            }
            if(order[idxCurrentOrder.get()].isInstantiated()) {
                int i = order[idxCurrentOrder.get()].getValue();
                int t = minAccValue(i);
                starts[i].instantiateTo(t, this);
                isOrdered[i].set(true);
            }
        } while(order[idxCurrentOrder.get()].isInstantiated());
    }

    @Override
    public ESat isEntailed() {
        for(int i = 0; i < order.length; i++) {
            if(!order[i].isInstantiated() || !starts[order[i].getValue()].isInstantiated()) {
                return ESat.UNDEFINED;
            }
        }
        return ESat.TRUE;
    }
}
