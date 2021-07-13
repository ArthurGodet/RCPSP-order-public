/*
@author Arthur Godet <arth.godet@gmail.com>
@since 24/09/2020
*/

package leftShifted;

/**
 * Profile data structure introduced in the following paper :
 * Gay, Hartet and Schaus. Simple and Scalable Time-Table Filtering for the Cumulative Constraint. In Principles and Practice of Constraint Programming - 21st International Conference, CP 2015, Cork, Ireland, August 31 - September 4, 2015, Proceedings. Ed. by Gilles Pesant. Vol. 9255. Lecture Notes in Computer Science. Springer, 2015, pp. 149-157.
 *
 * @author Arthur Godet <arth.godet@gmail.com>
 */
public class Profile {
    private int idx;
    private final int[] timePoints;
    private final int[] heights;
    private EventPointSeries eventPointSeries;

    public Profile(int nbTasks) {
        idx = 0;
        timePoints = new int[2 * (nbTasks + 1)];
        heights = new int[2 * (nbTasks + 1)];
        eventPointSeries = new EventPointSeries(nbTasks, 2);
    }

    public void clear() {
        idx = 0;
    }

    public int size() {
        return idx - 2;
    }

    public int getStartRectangle(int j) {
        return timePoints[j];
    }

    public int getEndRectangle(int j) {
        return timePoints[j + 1];
    }

    public int getHeightRectangle(int j) {
        return heights[j];
    }

    public int buildProfile(int nbT, int[] sub, int[] elb, int[] hlb) {
        clear();
        timePoints[idx] = Integer.MIN_VALUE;
        heights[idx] = 0;
        idx++;
        int maxHeight = 0;
        eventPointSeries.generateEvents(nbT, null, sub, elb, false, false, false);
        if(!eventPointSeries.isEmpty()) {
            int h = 0;
            int i = 0;
            while(!eventPointSeries.isEmpty()) {
                timePoints[idx] = eventPointSeries.getEvent().date;
                while(!eventPointSeries.isEmpty() && eventPointSeries.getEvent().date == timePoints[idx]) {
                    Event event = eventPointSeries.removeEvent();
                    h += event.type == Event.SCP ? hlb[event.indexTask] : -hlb[event.indexTask];
                }
                heights[idx] = h;
                idx++;
                maxHeight = Math.max(maxHeight, h);
                assert h >= 0;
            }
            assert h == 0;
        }
        timePoints[idx] = Integer.MAX_VALUE;
        heights[idx++] = 0;
        return maxHeight;
    }

    public int find(int date) {
        int i1 = 0;
        int i2 = idx - 2;
        while(i1 < i2) {
            int im = (i1 + i2) / 2;
            if(timePoints[im] <= date && date < timePoints[im + 1]) {
                i1 = im;
                i2 = im;
            } else if(timePoints[im] < date) {
                i1 = im + 1;
            } else if(timePoints[im] > date) {
                i2 = im - 1;
            }
        }
        return i1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Profile[");
        if(size() > 0) {
            for(int i = 0; i < size(); i++) {
                sb.append("<").append(timePoints[i]).append(",").append(timePoints[i+1]).append(",").append(heights[i]).append(">");
                if(i < size() - 1) {
                    sb.append(",");
                }
            }
            sb.append("<").append(timePoints[size()]).append(",").append(Integer.MAX_VALUE).append(",").append(0).append(">");
        } else {
            sb.append("<").append(Integer.MIN_VALUE).append(",").append(Integer.MAX_VALUE).append(",").append(0).append(">");
        }
        sb.append("]");
        return sb.toString();
    }
}
