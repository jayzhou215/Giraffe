package com.github.tcking.giraffe.helper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.github.tcking.example.activity.AppImageCroppingActivity;
import com.github.tcking.giraffe.core.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * <pre>
 * very easy  to get a photo in android,how to use:
 * 1.create a helper instance and set callback then call takePhoto() or choosePhoto(): new PhotoHelper(activity).callback(...).takePhoto();
 * 2.call photoHelper.onActivityResult in Activity or fragment onActivityResult method;
 *
 * more features:
 * new PhotoHelper(activity) //create a helper instance
 *      .quality(80) //compress image using quality 80
 *      .maxWidth(120,true) //try scale image unless with < 120dp,default is screen width
 *      .maxFileSizeKB(80) //try compress image unless file size < 80KB
 *      .cropping(true) //cropping the target image
 *      .autoRotate(true) //try rotate the image according to photo exif information (some samsung devices need to rotate)
 *      .callback(...) //implement PhotoHelper.CallBack call done() when action is done or error() where something is wrong
 *      .takePhoto() //or choosePhoto(), just do the job for you
 *
 * some util method:
 * 1.PhotoHelper.saveBitmap2File(Bitmap bitmap,File targetFile,int quality) //save bitmap to file
 * 2.PhotoHelper.getBitmap(File imageFile,int maxWidthInPx) //get Bitmap from a file and try scale image with max width
 * 3.PhotoHelper.rotateBitmap(Bitmap bitmap , int angle) //rotate image
 * 4.PhotoHelper.rotateBitmap(String src , Bitmap bitmap) //rotate image according to photo exif information
 * </pre>
 * Created by tc(mytcking@gmail.com) on 15/8/19.
 */
public class PhotoHelper {

    private static boolean autoRotate;
    private final Activity context;
    private String dir ="/giraffe/images";
    private final String FROM_CAMERA = "CAMERA";
    private final String FROM_GALLERY = "GALLERY";
    private String from= FROM_GALLERY;
    private int quality=80;
    private final int REQUESTCODE_TAKEPHOTO = 1;
    private final int REQUESTCODE_CHOOSEPHOTO = 2;
    private final int REQUESTCODE_CROPPING = 3;
    private File tempFile;
    private float maxWidth;
    private float maxHeight;
    private int maxFileSizeKB;
    private CallBack callback=new CallBack() {
        @Override
        public void done(File imageFile) {
            Log.d("PhotoHelper.done imageFile:{}",imageFile.getAbsoluteFile());
        }

        @Override
        public void error(Exception e) {
            Log.d("PhotoHelper.CallBack.error ",e);
        }
    };
    private File home;
    private boolean cropping;

    public PhotoHelper maxFileSizeKB(int maxFileSizeKB){
        this.maxFileSizeKB=maxFileSizeKB;
        return this;
    }

    public PhotoHelper cropping(boolean cropping){
        this.cropping=cropping;
        return this;
    }

    public PhotoHelper autoRotate(boolean autoRotate) {
        this.autoRotate=autoRotate;
        return this;
    }


    public PhotoHelper dir(String dir) {
        this.dir = dir;
        return this;
    }


    public PhotoHelper(Activity context) {
        this.context=context;
    }


    public PhotoHelper quality(int quality) {
        this.quality=quality;
        return this;
    }

    private void doIt() {
        insureDirs();
        if (maxWidth == 0 && maxHeight == 0) {
            maxScreenWidth();
        }
        try {
            if (FROM_CAMERA.equals(from)) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.Images.Media.ORIENTATION, 0);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(createTempFile()));
                context.startActivityForResult(intent, REQUESTCODE_TAKEPHOTO);
            } else {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                context.startActivityForResult(intent, REQUESTCODE_CHOOSEPHOTO);
            }
        } catch (Exception e) {
            callback.error(e);
        }
    }

    private void insureDirs() {
        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), this.dir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        home=dir;
    }


    private File createTempFile() throws IOException {
        File tempDir= Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)?
                context.getExternalCacheDir():context.getCacheDir();
        File file = new File(tempDir,"takePhoto.tmp");
        if (file.exists()) {
            file.delete();
        } else {
            File parent=file.getParentFile();
            if (parent!=null && !parent.exists()) {
                parent.mkdirs();
            }
        }

        tempFile = file;
        Log.d("PhotoHelper.createTempFile {}", tempFile.getAbsoluteFile());
        tempFile.createNewFile();
        return file;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUESTCODE_TAKEPHOTO) {
                if (tempFile != null && tempFile.exists()) {
                    tryCompress(tempFile);
                } else {
                    callback.error(new Exception("tempFile is not exists"));
                }
            } else if (requestCode == REQUESTCODE_CHOOSEPHOTO) {
                if (data != null) {
                    File inputImage = new File(data.getData().getPath());
                    tryCompress(inputImage);
                } else {
                    callback.error(new Exception("data is null"));
                }
            }else if (requestCode == REQUESTCODE_CROPPING) {
                if (data != null) {
                    File inputImage = (File) data.getSerializableExtra("imageFile");
                    File outputFile=createImageFile();
                    compress(inputImage, outputFile, quality, maxWidth, maxHeight, maxFileSizeKB);
                    callback.done(outputFile);
                } else {
                    callback.error(new Exception("data is null"));
                }
            }
        }
    }

    private void tryCompress(File tempFile) {
        File outputFile=createImageFile();
        if (cropping) {
            compress(tempFile, outputFile, quality, context.getResources().getDisplayMetrics().widthPixels, 0, maxFileSizeKB);
            Intent intent = new Intent(context, AppImageCroppingActivity.class);
            intent.putExtra("imageFile", outputFile);
            context.startActivityForResult(intent, REQUESTCODE_CROPPING);
        } else {
            compress(tempFile, outputFile, quality, maxWidth, maxHeight, maxFileSizeKB);
            callback.done(outputFile);
        }
    }

    private File createImageFile() {
        return new File(home, UUID.randomUUID().toString()+".jpeg");
    }


    /**
     * 先按照width, hight设置分辨率，再压缩到100k以下
     * @param inputImage
     * @param maxWidth 最大宽度
     * @param maxHeight 最大高度
     * @param maxFileSizeKB 文件最大大小(单位KB)
     * @return
     */
    public static void compress(File inputImage,File outputImage,int quality, float maxWidth, float maxHeight, int maxFileSizeKB) {
        try {
            BitmapFactory.Options op = new BitmapFactory.Options();
            if (maxWidth > 0 || maxHeight > 0) {//resize
                op.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(inputImage.getAbsolutePath(), op);
                int wRatio = 1;
                int hRatio = 1;
                if (maxWidth > 0) {
                    wRatio = (int) Math.ceil(op.outWidth / maxWidth); //计算宽度比例
                }
                if (maxHeight > 0) {
                    hRatio = (int) Math.ceil(op.outHeight / maxHeight); //计算高度比例
                }
                int scale = Math.max(wRatio, hRatio);
                op.inSampleSize = scale;
            }

            op.inJustDecodeBounds = false;
            Bitmap resizeBitmap = BitmapFactory.decodeFile(inputImage.getAbsolutePath(), op);
//            if (autoRotate) {
//                resizeBitmap=rotateBitmap(inputImage.getAbsolutePath(), resizeBitmap);
//            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resizeBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            if (maxFileSizeKB > 0) {
                int nextQuality = 100;
                while ((nextQuality =nextQuality-10)>=0 && baos.size() / 1024 > maxFileSizeKB) { // 循环判断如果压缩后图片是否大于100kb,大于继续压缩
                    if (nextQuality>=0) {
                        baos.reset();// 重置baos即清空baos
                        resizeBitmap.compress(Bitmap.CompressFormat.JPEG, nextQuality, baos);// 这里压缩options%，把压缩后的数据存放到baos中
                    }
                }
            }
            resizeBitmap.recycle();
            FileOutputStream fos = new FileOutputStream(outputImage);
            baos.writeTo(fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            Log.e("PhotoHelper.compressImage error", e);
        }
    }

    public PhotoHelper maxWidth(float _maxWidth) {
        return maxWidth(_maxWidth,false);
    }

    public PhotoHelper maxWidth(float _maxWidth,boolean dip) {
        this.maxWidth=dip?db2px(_maxWidth):_maxWidth;
        return this;
    }

    public int db2px(float dip){
        return (int) (dip * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public PhotoHelper maxScreenWidth() {
        this.maxWidth=context.getResources().getDisplayMetrics().widthPixels;
        return this;
    }

    public PhotoHelper maxHeight(float _maxHeight) {
        return maxHeight(_maxHeight, false);
    }
    public PhotoHelper maxHeight(float _maxHeight,boolean dip) {
        this.maxHeight=dip?db2px(_maxHeight):_maxHeight;
        return this;
    }


    public void takePhoto() {
        this.from = FROM_CAMERA;
        doIt();
    }

    public static Bitmap getBitmap(File imageFile) {
        return getBitmap(imageFile,0);
    }

    public static Bitmap getBitmap(File imageFile,int maxWidthInPx) {
        BitmapFactory.Options op = new BitmapFactory.Options();
        if (maxWidthInPx > 0) {//resize
            op.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imageFile.getAbsolutePath(), op);
            int wRatio = (int) Math.ceil(op.outWidth / maxWidthInPx); //计算宽度比例
            op.inSampleSize = wRatio;
        }
        op.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(imageFile.getAbsolutePath(), op);
    }

    public static void saveBitmap2File(Bitmap bitmap,File targetFile) throws IOException {
        saveBitmap2File(bitmap, targetFile, 100);
    }

    public static void saveBitmap2File(Bitmap bitmap,File targetFile,int quality) throws IOException {
        if (!targetFile.getParentFile().exists()) {
            targetFile.getParentFile().mkdirs();
        }
        targetFile.createNewFile();
        FileOutputStream fos = new FileOutputStream(targetFile);
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
        fos.flush();
        fos.close();
    }

    public PhotoHelper callback(CallBack callBack) {
        this.callback=callBack;
        return this;
    }

    public void choosePhoto() {
        from=FROM_GALLERY;
        doIt();
    }

    public interface CallBack{
        void done(File imageFile);

        void error(Exception e);
    }

    /**
     * @see http://sylvana.net/jpegcrop/exif_orientation.html
     */
    public static Bitmap rotateBitmap(String src, Bitmap bitmap) {
        try {
            int orientation = getExifOrientation(src);

            if (orientation == 1) {
                return bitmap;
            }

            Matrix matrix = new Matrix();
            switch (orientation) {
                case 2:
                    matrix.setScale(-1, 1);
                    break;
                case 3:
                    matrix.setRotate(180);
                    break;
                case 4:
                    matrix.setRotate(180);
                    matrix.postScale(-1, 1);
                    break;
                case 5:
                    matrix.setRotate(90);
                    matrix.postScale(-1, 1);
                    break;
                case 6:
                    matrix.setRotate(90);
                    break;
                case 7:
                    matrix.setRotate(-90);
                    matrix.postScale(-1, 1);
                    break;
                case 8:
                    matrix.setRotate(-90);
                    break;
                default:
                    return bitmap;
            }

            try {
                Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                bitmap.recycle();
                return oriented;
            } catch (OutOfMemoryError e) {
                Log.e("ExifUtil.rotateBitmap error", e);
                return bitmap;
            }
        } catch (IOException e) {
            Log.e("ExifUtil.rotateBitmap error", e);
        }

        return bitmap;
    }

    private static int getExifOrientation(String src) throws IOException {
        int orientation = 1;

        try {
            /**
             * if your are targeting only api level >= 5
             * ExifInterface exif = new ExifInterface(src);
             * orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
             */
            if (Build.VERSION.SDK_INT >= 5) {
                Class<?> exifClass = Class.forName("android.media.ExifInterface");
                Constructor<?> exifConstructor = exifClass.getConstructor(new Class[] { String.class });
                Object exifInstance = exifConstructor.newInstance(new Object[] { src });
                Method getAttributeInt = exifClass.getMethod("getAttributeInt", new Class[] { String.class, int.class });
                Field tagOrientationField = exifClass.getField("TAG_ORIENTATION");
                String tagOrientation = (String) tagOrientationField.get(null);
                orientation = (Integer) getAttributeInt.invoke(exifInstance, new Object[] { tagOrientation, 1});
            }
        } catch (Exception e) {
            Log.e("ExifUtil.getExifOrientation error", e);
        }

        return orientation;
    }

    public static Bitmap rotateBitmap(Bitmap bitmap , int angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle();
        return resizedBitmap;
    }
}
