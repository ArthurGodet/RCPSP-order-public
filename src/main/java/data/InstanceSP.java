package data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import gnu.trove.list.array.TIntArrayList;
import java.util.List;

public class InstanceSP {
    private final String name;
    private final int size;
    private final String type; // is either RCPSP, JSSP, MCJSSP
    private final List<Activity> activities;
    private final int[] resourcesAvailabilities;
    private final int horizon;
    @JsonIgnore
    private int[][] predecessors = null;
    @JsonIgnore
    private int[][] successors = null;

    @JsonCreator
    public InstanceSP(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("activities") List<Activity> activities,
            @JsonProperty("resourcesAvailabilities") int[] resourcesAvailabilities,
            @JsonProperty("horizon") int horizon) {
        this.name = name;
        this.size = activities.size();
        this.type = type;
        this.activities = activities;
        this.resourcesAvailabilities = resourcesAvailabilities;
        this.horizon = horizon;

        buildPredecessors();
        buildSuccessors();
    }

    public String getName() {
        return this.name;
    }

    public int getResourceAvailability(int resourceIdx) {
        return resourcesAvailabilities[resourceIdx];
    }

    public int[] getResourcesAvailabilities() {
        return resourcesAvailabilities;
    }

    public List<Activity> getActivities() {
        return activities;
    }

    public Activity getActivity(int id) {
        for(Activity j : activities){
            if(j.getID() == id){
                return j;
            }
        }
        return null;
    }

    public int getHorizon() {
        return horizon;
    }

    public String getType() {
        return type;
    }

    private static boolean contains(int[] array, int value) {
        for(int a : array) {
            if(a == value) {
                return true;
            }
        }
        return false;
    }

    private void buildSuccessors() {
        this.successors = new int[activities.size()][];
        for(int i = 0; i < successors.length; i++) {
            successors[i] = activities.get(i).getSuccessors();
        }
    }

    private void buildPredecessors() {
        predecessors = new int[activities.size()][];
        TIntArrayList list = new TIntArrayList(activities.size());
        for(int i = 0; i<predecessors.length; i++) {
            list.clear();
            for(int j = 0; j< activities.size(); j++) {
                if(contains(activities.get(j).getSuccessors(), activities.get(i).getID())) {
                    list.add(j);
                }
            }
            predecessors[i] = list.toArray();
        }
    }

    public int[][] getPredecessors() {
        if(this.predecessors == null) {
            buildPredecessors();
        }
        return this.predecessors;
    }

    public int[][] getSuccessors() {
        return this.successors;
    }

    public int getSize() {
        return size;
    }

}
