/*
@author Arthur Godet <arth.godet@gmail.com>
@since 25/09/2020
*/

package leftShifted;

import java.util.Comparator;
import org.chocosolver.util.sort.ArraySort;

/**
 * EventPointSeries data structure introduced in the following paper:
 * Letort, Beldiceanu, and Carlsson. A Scalable SweepAlgorithm for the cumulative Constraint. In Principles and Practice of Constraint Programming - 18th International Conference, CP 2012, Québec City, QC, Canada, October 8-12, 2012. Proceedings. Ed. by Michela Milano. Vol. 7514. Lecture Notes in Computer Science. Springer, 2012, pp. 439–45.
 *
 * @author Arthur Godet <arth.godet@gmail.com>
 */
public class EventPointSeries {
    protected Event[] eventsArray;
    protected int nbEvents;
    protected int timeIndex;
    protected Comparator<Event> comparator = Event::compareTo;
    protected ArraySort<Event> sort;

    public EventPointSeries(int nbTasks, int nbMaxEventsPerTask) {
        eventsArray = new Event[nbMaxEventsPerTask * nbTasks];
        for(int i = 0; i < eventsArray.length; i++) {
            eventsArray[i] = new Event();
        }
        sort = new ArraySort<>(eventsArray.length, true, false);
    }

    public boolean isEmpty() {
        return timeIndex >= nbEvents;
    }

    public int size() {
        return nbEvents - timeIndex;
    }

    public void generateEvents(int nbT, int[] slb, int[] sub, int[] elb, boolean generatePREvents, boolean generateCCPEvents, boolean mergeScpAndCcpEvents) {
        nbEvents = 0;
        timeIndex = 0;
        for(int i = 0; i < nbT; i++) {
            if(generatePREvents) {
                // start min can be filtered
                if(slb[i] < sub[i]) {
                    eventsArray[nbEvents++].set(Event.PR, i, slb[i]);
                }
            }
            if(mergeScpAndCcpEvents) {
                eventsArray[nbEvents++].set(Event.SCP, i, sub[i]);
                if(sub[i] < elb[i]) {
                    eventsArray[nbEvents++].set(Event.ECP, i, elb[i]);
                }
            } else {
                // a compulsory part exists
                if(sub[i] < elb[i]) {
                    eventsArray[nbEvents++].set(Event.SCP, i, sub[i]);
                    eventsArray[nbEvents++].set(Event.ECP, i, elb[i]);
                } else if(generateCCPEvents) { // conditional compulsory part
                    eventsArray[nbEvents++].set(Event.CCP, i, sub[i]);
                }
            }
        }
        if(!isEmpty()) {
            sort.sort(eventsArray, nbEvents, comparator);
        }
    }

    public Event getEvent() {
        return eventsArray[timeIndex];
    }

    public Event removeEvent() {
        return eventsArray[timeIndex++];
    }

    public void swap(int index1, int index2) {
        Event tmp = eventsArray[index1];
        eventsArray[index1] = eventsArray[index2];
        eventsArray[index2] = tmp;
    }

    public void addEvent(int type, int idxTask, int date) {
        int pos = nbEvents;
        eventsArray[nbEvents++].set(type, idxTask, date);
        while(timeIndex <= pos - 1 && comparator.compare(eventsArray[pos - 1], eventsArray[pos]) > 0) {
            swap(pos - 1, pos);
            pos--;
        }
    }

    @FunctionalInterface
    public interface Updater {
        void update(Event e);
    }

    public void updateEvent(int type, int idxTask, Updater updater) {
        int pos = timeIndex;
        while(pos < nbEvents && (eventsArray[pos].type != type || eventsArray[pos].indexTask != idxTask)) {
            pos++;
        }
        if(pos < nbEvents) {
            updater.update(eventsArray[pos]);
            while(pos < nbEvents - 1 && comparator.compare(eventsArray[pos], eventsArray[pos + 1]) > 0) {
                swap(pos, pos + 1);
                pos++;
            }
        }
    }

    public void updateCompulsoryPartEvents(int idxTask, int[] sub, int[] elb) {
        if(sub[idxTask] < elb[idxTask]) {
            updateEvent(Event.SCP, idxTask, e -> e.date = sub[idxTask]);
            updateEvent(Event.ECP, idxTask, e -> e.date = elb[idxTask]);
        }
    }

    public int getNextDate() {
        int pos = timeIndex;
        int date = eventsArray[pos].date;
        while(pos < nbEvents && eventsArray[pos].date == date) {
            pos++;
        }
        if(pos < nbEvents) {
            return eventsArray[pos].date;
        } else {
            return date;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("EventPointSeries[");
        for(int pos = timeIndex; pos < nbEvents; pos++) {
            sb.append(eventsArray[pos]);
            if(pos < nbEvents - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
