package com.project.lazyloadingadapter.objects;
import android.widget.ImageView;
import android.widget.ViewSwitcher;
public class QueueObject<E>
{
    private int mPosition;
    private E mPathIDOrUri;
    private ViewSwitcher mViewSwitcher;
    private ImageView mImageView;
    public QueueObject(int position, E pathIDOrUri, ViewSwitcher viewSwitcher, ImageView imageView)
    {
	mPosition = position;
	mPathIDOrUri = pathIDOrUri;
	mViewSwitcher = viewSwitcher;
	mImageView = imageView;
    }
    public ViewSwitcher getViewSwitcher()
    {
	return mViewSwitcher;
    }
    public ImageView getImage()
    {
	return mImageView;
    }
    public int getPosition()
    {
	return mPosition;
    }
    public E getPathIDOrUri()
    {
	return mPathIDOrUri;
    }
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object object)
    {
	if (!(object instanceof QueueObject))
	    return false;
	if (((QueueObject<E>) object).mViewSwitcher == mViewSwitcher)
	{
	    return true;
	}
	return false;
    }
}