/**
 * 
 */
package com.project.lazyloadingadapter.objects;
import android.graphics.Bitmap;
import android.support.v4.util.LruCache;
/**
 * @author nseidm1
 * 
 */
public class CustomLRUCache<E> extends LruCache<E, Bitmap>
{
    /**
     * This custom LRU cache is bound in MB, not in quantity of items
     * 
     * @param maxSize
     */
    public CustomLRUCache(int maxSize)
    {
	super(maxSize);
    }
    @Override
    protected int sizeOf(E key, Bitmap value)
    {
	return value.getRowBytes() * value.getHeight();
    }
}