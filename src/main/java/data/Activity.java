package data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;

public class Activity implements Comparable<Activity> {
    private final int id;
    private final int duration;
    private final int[] resourceConsumption;
    private final int[] successors;

    @JsonCreator
    public Activity(
            @JsonProperty("id") int id,
            @JsonProperty("duration") int duration,
            @JsonProperty("resourceConsumption") int[] resourceConsumption,
            @JsonProperty("successors") int[] successors) {
        this.id = id;
        this.duration = duration;
        this.resourceConsumption = resourceConsumption;
        this.successors = successors;
    }

    public int getID(){
        return this.id;
    }

    public int getDuration() {
        return this.duration;
    }

    public int[] getResourceConsumption() {
        return this.resourceConsumption;
    }

    public int[] getSuccessors() {
        return this.successors;
    }

    public int compareTo(Activity j){
        if(this.duration == j.duration) {
            return Integer.compare(this.id, j.id);
        }
        return Integer.compare(this.duration, j.duration);
    }

    @Override
    public String toString() {
        return "Activity("+id+","+duration+","+ Arrays.toString(resourceConsumption)+","+Arrays.toString(successors)+")";
    }

    @Override
    public Activity clone() {
        return new Activity(id, duration, resourceConsumption, successors);
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof Activity) {
            return this.id == ((Activity)o).id;
        }
        return false;
    }
}
