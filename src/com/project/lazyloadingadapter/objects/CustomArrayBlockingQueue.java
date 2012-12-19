/**
 * 
 */
package com.project.lazyloadingadapter.objects;
import java.util.concurrent.ArrayBlockingQueue;
/**
 * @author nseidm1
 * @param <E>
 * 
 */
public class CustomArrayBlockingQueue<E> extends ArrayBlockingQueue<QueueObject<E>>
{
    private static final long serialVersionUID = 6502527787864416199L;
    /**
     * @param capacity
     * @param fair
     */
    public CustomArrayBlockingQueue(int capacity, boolean fair)
    {
	super(capacity, fair);
    }
}