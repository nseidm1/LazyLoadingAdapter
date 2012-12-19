package com.project.lazyloadingadapter;
import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ViewSwitcher;
import com.project.lazyloadingadapter.helpers.PrivateClearCacheTask;
import com.project.lazyloadingadapter.objects.ClearCacheCallback;
import com.project.lazyloadingadapter.objects.CustomLRUCache;
import com.project.lazyloadingadapter.objects.LoadingCompleteCallback;
import com.project.lazyloadingadapter.objects.QueueObject;
import com.project.lazyloadingadapter.objects.UnsupportedContentException;
import com.project.lazyloadingadapter.support.RetrieverThread;
/**
 * @author Noah Seidman
 * @param <E>
 *            Specify the type of media this adapter will be using. The
 *            parameter types include Strings for local file system paths, Longs
 *            for Gallery content provider thumbnail IDs
 *            "Thumbnails.getThumbnail()", and Uris for remote http media
 */
@SuppressWarnings("deprecation")
public class LazyLoadingAdapter<E> extends BaseAdapter implements LoadingCompleteCallback<E>
{
    private Context mContext;
    private int mWidth;
    private int mHeight;
    private int mHightlightColor = Color.YELLOW;
    private int mDegressRotation = 0;
    private boolean mIsImages;
    private ViewHolder mHolder;
    private List<Integer> mAddHighlight = new ArrayList<Integer>();
    private ArrayList<E> mPathsIDsOrUris = new ArrayList<E>();
    protected static final int PROGRESSBARINDEX = 0;
    protected static final int IMAGEVIEWINDEX = 1;
    protected View mView;
    protected Handler mHandler = new Handler();
    protected RetrieverThread<E> mImageRetrieverThread;
    protected CustomLRUCache<E> mCache;
    /**
     * Warning - Do not forget to close() the adapter in onDestroy to stop the
     * loader thread
     * <p>
     * A lazy loading image adapter using an array blocking queue.This adapter
     * works well with images of any size.
     * <p>
     * It gracefully accommodates the memory requirements of image decoding
     * implementing a prescaling algorithm when required and using a seamless
     * out of memory accommodation technique. The adapter supports AbsListViews
     * which are ListViews and GridViews, as well as Gallery widgets.
     * <p>
     * Provide this adapter with a reference to the AbsListView or Gallery, the
     * height/width of the desired images, a Object List of the image
     * paths/ThumbID (may be URIs of remote http locations), and a size for the
     * built in LRU cache in Megabytes
     * <p>
     * The Object list can contain 3 object types. Supply Longs if you want the
     * IDs of Gallery thumbnails. Supply strings if you want the full
     * image/video paths on the local filesystem. For videos make sure the path
     * contains the full extension of the video! Supply Uris if you want remote
     * images from the internet. Curerntly Uris will default to image decoding
     * and do not support videos!
     * <p>
     * DON'T forger to close() this adapter to stop the lazy loading thread!!!
     * If forgotten it will leak bad! You should also cleanup the cache
     * directory, on occasion, if your using remote data.
     * 
     * @param context
     * @param view
     *            Your AbsListView or your Gallery Widget
     * @param height
     *            The height of your image
     * @param width
     *            The width of your image
     * @param pathsOrIds
     *            A List of Strings, Long IDs for "Thumbnails.getThumbnail()"
     *            from the phone's Image/Video content provider, or URIs of http
     *            addresses
     * @param size
     *            Specify the size of the LRU cache in megabytes
     * @param isImages
     *            If a List of String paths are provided are they image files or
     *            video files? If a List of Long IDs is provided, are they for
     *            the Images or Video content provider?
     * @throws UnsupportedContentException
     *             Per design only strings of local paths, Longs of thumb IDs,
     *             or URIs of remote media are supported
     */
    public LazyLoadingAdapter(Context context, View view, int height, int width, ArrayList<E> pathsIDsOrUris, int size, boolean isImages) throws UnsupportedContentException
    {
	if (pathsIDsOrUris != null)
	{
	    testData(pathsIDsOrUris);
	    mPathsIDsOrUris = pathsIDsOrUris;
	}
	testView(view);
	mContext = context;
	mView = view;
	mWidth = width;
	mHeight = height;
	mIsImages = isImages;
	mCache = new CustomLRUCache<E>(size * 1024 * 1024);
	// Provide the loader thread with some info beforehand including the
	// cache, width, and height. The dimensions of the
	// desired thumbnail are needed for decoding purposes
	mImageRetrieverThread = new RetrieverThread<E>(mContext, mHandler, this, mCache, mWidth, mHeight, mIsImages);
	mImageRetrieverThread.start();
    }
    private void testData(List<?> pathsOrIds) throws UnsupportedContentException
    {
	for (Object object : pathsOrIds)
	{
	    if (!(object instanceof Long) && !(object instanceof String) && !(object instanceof Uri))
		throw new UnsupportedContentException("List content contains unsupported data. " + "This adapter accepts a List of String paths to images or video, "
			+ "Long values referring to the thumbnail of a Gallery image/video, or Uri " + "locations of remote http content.");
	}
    }
    private void testView(View view) throws UnsupportedContentException
    {
	if (!(view instanceof Gallery) && !(view instanceof AbsListView))
	    throw new UnsupportedContentException("The supplied view can only be an AbsListView or a Gallery");
    }
    /**
     * @param pathOrId
     *            Your List items are used as the key for the LRU cache. Specify
     *            a particular item to remove that particular thumb from the LRU
     *            cache.
     */
    public void removeFromCache(E pathIDOrUri)
    {
	mCache.remove(pathIDOrUri);
    }
    /**
     * @param height
     *            Specify the desired height of the view your adapter will be
     *            filling.
     */
    public void setImageHeight(int height)
    {
	mHeight = height;
    }
    /**
     * @param width
     *            Specify the desired width of the view your adapter will be
     *            filling.
     */
    public void setImageWidth(int width)
    {
	mWidth = width;
    }
    /**
     * @param position
     *            Add the specified highlight color to the position. The default
     *            highlight color is yellow, use setHighlightColor() to change
     *            the value.
     */
    public void addHighlight(int position)
    {
	mAddHighlight.add(position);
    }
    /**
     * @param highlightColor
     *            Supply a Color value or use Color.Parse() to use a custom hex
     *            value.
     */
    public void setHighlightColor(int highlightColor)
    {
	mHightlightColor = highlightColor;
    }
    /**
     * @param position
     *            Remove the highlight from the specific item position.
     */
    public void removeHighlight(int position)
    {
	mAddHighlight.remove((Object) position);
    }
    /**
     * Clear all highlighted position. You'll want to call
     * notifyDataSetChanged() afterwards.
     */
    public void clearHighlights()
    {
	mAddHighlight.clear();
    }
    /**
     * @param pathsOrIds
     *            A List of Strings, Long IDs for "Thumbnails.getThumbnail()"
     *            from the phone's Image/Video content provider, or URIs of http
     *            addresses.
     *            <p>
     *            You'll likely want to call clearCache() after changing your
     *            data.
     *            <p>
     *            Don't forget to call notifyDataSetChanged()!
     * @throws UnsupportedContentException
     *             Per design only strings of local paths, Longs of thumb IDs,
     *             or URIs of remote media are supported.
     */
    public void setPathsIDsOrUris(ArrayList<E> pathsIDsOrUris) throws UnsupportedContentException
    {
	testData(pathsIDsOrUris);
	mPathsIDsOrUris = pathsIDsOrUris;
    }
    /**
     * @param view
     *            Your AbsListView or your Gallery Widget.
     * @throws UnsupportedContentException
     *             Per design on AbsListViews and deprecated Gallery widgets are
     *             supported.
     */
    public void setView(View view) throws UnsupportedContentException
    {
	testView(view);
	mView = view;
    }
    @Override
    public int getCount()
    {
	return mPathsIDsOrUris.size();
    }
    @Override
    public E getItem(int position)
    {
	return mPathsIDsOrUris.get(position);
    }
    /**
     * This is absolutely mandatory to use in onDestroy() or wherever applicable
     * or appropriate. If this is not called you will leak the retriever thread.
     * Leakie is no goodie.
     */
    public void close()
    {
	mImageRetrieverThread.stopThread();
    }
    /**
     * Clear the LRU cache of all images. You'll likely want to call
     * notifyDataSetChanged() afterwards.
     */
    public void clearCache()
    {
	mCache.evictAll();
    }
    /**
     * @param clearCacheCallback
     *            This method is used to clear your app's cache directory. The
     *            cache directory is used for remote media downloads.
     *            <p>
     *            Provide a interface to call when the cache directory has been
     *            completely emptied.
     *            <p>
     *            After the cache directory is emptied you'll likely want to
     *            clear the LRU cache using clearCache(), and call
     *            notifyDataSetChaged().
     */
    public void clearCacheWithCallback(ClearCacheCallback clearCacheCallback)
    {
	new PrivateClearCacheTask(mContext, clearCacheCallback).execute();
    }
    /**
     * @param degrees
     *            Supply the degress to rotate all the supplied image
     *            thumbnails. The rotation will not be cached, and will occur in
     *            realtime, which may effect scrolling.
     */
    public void setRotation(int degrees)
    {
	mDegressRotation = degrees;
    }
    /**
     * @return Get the currently set value for the rotation to apply.
     */
    public int getRotation()
    {
	return mDegressRotation;
    }
    @Override
    public long getItemId(int position)
    {
	return position;
    }
    protected class ViewHolder
    {
	ImageView image;
    }
    @SuppressWarnings("unchecked")
    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
	if (convertView == null)
	{
	    convertView = processConvertView(convertView);
	}
	else
	{
	    mHolder = (ViewHolder) convertView.getTag();
	}
	// Retrieve the image either from the cache or load it into the cache,
	// then display accordingly
	processHighlights(position);
	retrieveImage((ViewSwitcher) convertView, position);
	return convertView;
    }
    protected void processHighlights(int position)
    {
	if (mAddHighlight.contains(position))
	{
	    mHolder.image.setPadding(5, 5, 5, 5);
	    mHolder.image.setBackgroundColor(mHightlightColor);
	}
	else
	{
	    mHolder.image.setPadding(0, 0, 0, 0);
	    mHolder.image.setBackgroundColor(Color.parseColor("#00000000"));
	}
    }
    protected Bitmap processRotation(Bitmap bitmap)
    {
	if (getRotation() == 0 || (getRotation() % 360) == 0)
	{
	    return bitmap;
	}
	Matrix matrix = new Matrix();
	matrix.setRotate(getRotation(), bitmap.getWidth() / 2, bitmap.getHeight() / 2);
	return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
    }
    protected void retrieveImage(ViewSwitcher convertView, int position)
    {
	// To accommodate data change race conditions only process the view of
	// the size of the data list of > the position requested
	if (mPathsIDsOrUris.size() != 0 && mPathsIDsOrUris.size() > position)
	{
	    convertView.setDisplayedChild(LazyLoadingAdapter.PROGRESSBARINDEX);
	    mImageRetrieverThread.loadImage(new QueueObject<E>(position, mPathsIDsOrUris.get(position), convertView, mHolder.image));
	}
    }
    protected View processConvertView(View convertView)
    {
	mHolder = new ViewHolder();
	convertView = new ViewSwitcher(mContext);
	LinearLayout progressBarContainer = new LinearLayout(mContext);
	progressBarContainer = initProgressBarContainer(progressBarContainer);
	mHolder.image = new ImageView(mContext);
	mHolder.image.setScaleType(ScaleType.FIT_CENTER);
	mHolder.image.setLayoutParams(new LinearLayout.LayoutParams(mWidth, mWidth));
	((ViewSwitcher) convertView).addView(progressBarContainer);
	((ViewSwitcher) convertView).addView(mHolder.image);
	convertView.setTag(mHolder);
	return convertView;
    }
    // Add a progress bar to the progress bar container and ensure that it's
    // centered accordingly
    protected LinearLayout initProgressBarContainer(LinearLayout progressBarContainer)
    {
	progressBarContainer.setLayoutParams(new LinearLayout.LayoutParams(mWidth, mHeight));
	progressBarContainer.setGravity(Gravity.CENTER);
	ProgressBar progress = new ProgressBar(mContext, null, android.R.attr.progressBarStyleSmall);
	progress.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
	progressBarContainer.addView(progress);
	return progressBarContainer;
    }
    @Override
    public void updateImageInUI(final QueueObject<E> object, final Bitmap image)
    {
	mHandler.post(new Runnable()
	{
	    public void run()
	    {
		// If the absView is scrolling fast by the time the image is
		// retrieved it may no longer be on the screen
		// This will alleviate posting an image to the respective view
		// if the position is no longer visible
		if (mView instanceof AbsListView && object.getPosition() >= ((AbsListView) mView).getFirstVisiblePosition() && object.getPosition() <= ((AbsListView) mView).getLastVisiblePosition())
		{
		    object.getImage().setImageBitmap(processRotation(image));
		    object.getViewSwitcher().setDisplayedChild(IMAGEVIEWINDEX);
		}
		else if (mView instanceof Gallery)
		{
		    object.getImage().setImageBitmap(processRotation(image));
		    object.getViewSwitcher().setDisplayedChild(IMAGEVIEWINDEX);
		}
	    }
	});
    }
}