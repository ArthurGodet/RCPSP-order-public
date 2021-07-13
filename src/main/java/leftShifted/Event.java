/*
@author Arthur Godet <arth.godet@gmail.com>
@since 24/09/2020
*/

package leftShifted;

/**
 * Event data structure introduced in the following paper:
 *
 *
 * @author Arthur Godet <arth.godet@gmail.com>
 */
public class Event implements Comparable<Event> {
    public final static int SCP = 0, ECP = 1, CCP = 2, PR = 3;

    protected int type;
    protected int indexTask;
    protected int date;

    public Event() {

    }

    public Event(int type, int indexTask, int date) {
        this.type = type;
        this.indexTask = indexTask;
        this.date = date;
    }

    public void set(int type, int indexTask, int date) {
        this.type = type;
        this.indexTask = indexTask;
        this.date = date;
    }

    @Override
    public int compareTo(Event o) {
        if(this.date == o.date){
            return this.type - o.type;
        }
        return this.date - o.date;
    }

    @Override
    public String toString() {
        String typeStr;
        switch(this.type) {
            case SCP: typeStr = "SCP"; break;
            case ECP: typeStr = "ECP"; break;
            case CCP: typeStr = "CCP"; break;
            case PR: typeStr = "PR"; break;
            default: throw new UnsupportedOperationException();
        }
        return "Event<"+typeStr+","+indexTask+","+date+">";
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Event) {
            Event event = (Event) obj;
            return this.type == event.type && this.date == event.date && this.indexTask == event.indexTask;
        }
        return false;
    }
}