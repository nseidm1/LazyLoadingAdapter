package com.project.lazyloadingadapter.objects;

import android.graphics.Bitmap;

public interface LoadingCompleteCallback<E>
{
    public void updateImageInUI(QueueObject<E> object, Bitmap image);
}