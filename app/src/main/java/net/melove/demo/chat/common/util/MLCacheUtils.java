package net.melove.demo.chat.common.util;

import android.graphics.Bitmap;
import android.util.LruCache;

/**
 * Created by lzan13 on 2016/3/28.
 */
public class MLCacheUtils {

    private static MLCacheUtils instance;
    private LruCache<String, Bitmap> cache = null;


    private MLCacheUtils() {
        cache = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }
        };
    }

    /**
     * 获取当前对象的单例实例
     *
     * @return 返回当前类的实例
     */
    public static synchronized MLCacheUtils getInstance() {
        if (instance == null) {
            instance = new MLCacheUtils();
        }
        return instance;
    }

    /**
     * 将Bimap对象添加进缓存中
     *
     * @param key    保存Bitmap对象到缓存的Key
     * @param bitmap 需要保存到缓存中的bitmap
     * @return 返回保存成功的Bitmap对象
     */
    public Bitmap putBitmap(String key, Bitmap bitmap) {
        return cache.put(key, bitmap);
    }

    /**
     * 根据Key 获取还从中的Bitmap对象
     *
     * @param key 需要获取的缓存Bitmap的Key
     * @return 返回需要获取的Bitmap
     */
    public Bitmap optBitmap(String key) {
        return cache.get(key);
    }

}
