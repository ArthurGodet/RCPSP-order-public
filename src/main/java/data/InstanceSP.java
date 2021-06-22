package data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import gnu.trove.list.array.TIntArrayList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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
    @JsonIgnore
    private int[] successorsChainLength = null;
    @JsonIgnore
    private int[] successorsChainLengthWithTime = null;

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

    private int getSuccessorsLength(int i, boolean withTime) {
        int toAdd = (withTime ? activities.get(i).getDuration() : 1);
        if(activities.get(i).getSuccessors().length == 0) {
            return toAdd;
        } else {
            return toAdd + Arrays.stream(activities.get(i).getSuccessors()).map(k -> getSuccessorsLength(k, withTime)).max().getAsInt();
        }
    }

    private void buildSuccessorsChainLength() {
        successorsChainLength = new int[activities.size()];
        for(int i = 0; i < successorsChainLength.length; i++) {
            successorsChainLength[i] = getSuccessorsLength(i, false);
        }
    }

    private void buildSuccessorsChainLengthWithTime() {
        successorsChainLengthWithTime = new int[activities.size()];
        for(int i = 0; i < successorsChainLengthWithTime.length; i++) {
            successorsChainLengthWithTime[i] = getSuccessorsLength(i, true);
        }
    }

    public int[] getSuccessorsChainLengthWithTime() {
        if(this.successorsChainLengthWithTime == null) {
            buildSuccessorsChainLengthWithTime();
        }
        return this.successorsChainLengthWithTime;
    }

    public int[] getSuccessorsChainLength() {
        if(this.successorsChainLength == null) {
            buildSuccessorsChainLength();
        }
        return this.successorsChainLength;
    }

    public int getSize() {
        return size;
    }

    ////////////////////////////////////////////////////////////////
    ////////////////////// Instance generator //////////////////////
    ////////////////////////////////////////////////////////////////

    private static final Random RND = new Random(0);
    private static final TIntArrayList list = new TIntArrayList();
    private static final TIntArrayList list2 = new TIntArrayList();

    private static int generateNonZeroInteger(int max) {
        int a;
        do {
            a = RND.nextInt(max);
        } while(a == 0);
        return a;
    }

    private static InstanceSP generateInstance(int size, int maxDuration, int density, String name) {
        list.clear();
        for(int i = 0; i < size; i++) {
            for(int j = i + 1; j < size; j++) {
                list.add(i*size + j);
            }
        }
        list.shuffle(RND);
        List<Activity> activities = new ArrayList<>();
        int horizon = 0;
        int[] resourceAvailabilities = new int[4];
        for(int j = 0; j < resourceAvailabilities.length; j++) {
            resourceAvailabilities[j] = generateNonZeroInteger(10);
        }
        for(int i = 0; i < size; i++) {
            int duration = generateNonZeroInteger(maxDuration);
            horizon += duration;
            int[] resourceConsumption = new int[resourceAvailabilities.length];
            do {
                for(int j = 0; j < resourceAvailabilities.length; j++) {
                    if(resourceAvailabilities[j] > 1) {
                        resourceConsumption[j] = RND.nextInt(resourceAvailabilities[j]);
                    } else {
                        resourceConsumption[j] = 1;
                    }
                }
            } while(Arrays.stream(resourceConsumption).sum() == 0);
            list2.clear();
            for(int k = 0; k < list.size() * density / 100; k++) {
                int tmp = list.getQuick(k);
                if(tmp / size == i) {
                    list2.add(tmp % size);
                }
            }
            list2.sort();
            Activity activity = new Activity(i, duration, resourceConsumption, list2.toArray());
            activities.add(activity);
        }
        return new InstanceSP(name, "RCPSP", activities, resourceAvailabilities, horizon);
    }

    public static void main(String[] args) throws IOException {
        /*
        FileWriter fw = new FileWriter("visu.cmd");
        for(int density = 10; density <= 100; density += 10) {
            InstanceSP instanceSP = generateInstance(20, 30, density, "test_"+density);
            String str = "data/RCPSP/test/"+instanceSP.getName();
            Factory.toFile(str+".json", instanceSP);
            Factory.writeWorldGVFile(new File(str+".json"));
            fw.write("dot -x -Tpng -o"+str+".png "+str+".gv\n");
        }
        fw.close();
        //*/
        for(int size = 10; size <= 50; size += 5) {
            for(int density = 10; density <= 80; density += 10) {
                for(int k = 0; k < 5; k++) {
                    System.out.println("rcpsp_"+size+"_"+density+"_"+k);
                    InstanceSP instanceSP = generateInstance(size, 30, density, "rcpsp_"+size+"_"+density+"_"+k);
                    String str = "data/RCPSP/rnd/"+instanceSP.getName();
                    Factory.toFile(str+".json", instanceSP);
                }
            }
        }
    }
}
