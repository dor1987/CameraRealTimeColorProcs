package ashush.pdactech;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.constraint.solver.widgets.Rectangle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.graphics.Palette;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;


public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERM_RES = 0;


    //Palettes
    private Palette.Swatch vibrantSwatch;
    private Palette.Swatch lightVibrantSwatch;
    private Palette.Swatch darkVibrantSwatch;
    private Palette.Swatch mutedSwatch;
    private Palette.Swatch lightMutedSwatch;
    private Palette.Swatch darkMutedSwatch;

    //views
    TextView test1;

    private TextureView textureView;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            setupCamera(i,i1);
            //fix rotation bug
            transformImage(i,i1);
            connectCamera();

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {


        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };


    private CameraDevice mCameraDevice;
    //will return CameraDevica when its available

    private CameraDevice.StateCallback mCamStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
        mCameraDevice = cameraDevice;
            //Toast.makeText(MainActivity.this, "Camera connection successful", Toast.LENGTH_SHORT).show();
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    //creating background thread
    private HandlerThread mBackGroundHandlerThread;
    private Handler mBackGroundHandler;


    private String mCameraId;
    private Size mPreviewSize;
    private CaptureRequest.Builder mCaptureRequestBuilder;

    private ImageReader mImageReader;

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //Log.d(TAG, "The onImageAvailable thread id: " + Thread.currentThread().getId());
            Image readImage = reader.acquireLatestImage();


           // process(bitmap);
            readImage.close();


/*
            ByteBuffer buffer = readImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Bitmap myBitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length,null);
*/




        }
    };
/*
    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback(){
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);

        }
    };
*/


    //Orientations
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,0);
        ORIENTATIONS.append(Surface.ROTATION_90,90);
        ORIENTATIONS.append(Surface.ROTATION_180,180);
        ORIENTATIONS.append(Surface.ROTATION_270,270);
    }


    private static class CompareSizeByArea implements Comparator<Size>{

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight()/
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        test1 = (TextView) findViewById(R.id.textViewColorNum1Percent);

        textureView = (TextureView) findViewById(R.id.cameraTextureView);
        //make landscape orientation
        setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERM_RES){
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, "Cant work without Camera permission", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackGroundThread();

        //check if textureView is available
        if(textureView.isAvailable()){
            setupCamera(textureView.getWidth(),textureView.getHeight());
            //fix rotation bug
            transformImage(textureView.getWidth(),textureView.getHeight());
            connectCamera();
        }

        else{
            textureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        startBackGroundThread();

        super.onPause();

    }

    private void closeCamera(){
        if(mCameraDevice != null){
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private void setupCamera(int width, int height){
        CameraManager cameraManager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        //Getting the Id of the rear camera
        try {
            for(String cameraId : cameraManager.getCameraIdList()){
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                else{
                    StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                    int totalRotation = sensorToDeviceRotation(cameraCharacteristics,deviceOrientation);

                    boolean swapRotation = totalRotation == 90 || totalRotation == 270;
                    int rotatedWidth  = width;
                    int rotatedHeight = height;

                    //adjust rotation
                    if(swapRotation){
                        rotatedWidth = height;
                        rotatedHeight = width;
                    }
                    mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),rotatedWidth,rotatedHeight);
                    mCameraId = cameraId;

                    mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                            ImageFormat.YUV_420_888, 3);
                    mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,mBackGroundHandler);

                    return;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void connectCamera(){
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            //check permission for Marshmelow and up
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED){
                    cameraManager.openCamera(mCameraId,mCamStateCallback,mBackGroundHandler);

                }
                else{
                    //no permission
                    if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                        Toast.makeText(this, "Camera access is needed", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA_PERM_RES);
                }
            }
            else{
                cameraManager.openCamera(mCameraId,mCamStateCallback,mBackGroundHandler);

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview(){
    //start the preview display on the screen
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {

                        try {
                        cameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                                null,mBackGroundHandler);
                        //todo instead of null maybe i can porcses the data
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }

    private void startBackGroundThread(){
        mBackGroundHandlerThread = new HandlerThread("BackgroundThread");
        mBackGroundHandlerThread.start();
        mBackGroundHandler = new Handler(mBackGroundHandlerThread.getLooper());
    }

    private void stopBackGroundThread(){
        mBackGroundHandlerThread.quitSafely();
        try {
            mBackGroundHandlerThread.join();
            mBackGroundHandlerThread = null;
            mBackGroundHandler = null;

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrentation){
       //calc the needed orientations
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrentation = ORIENTATIONS.get(deviceOrentation);

        return(sensorOrientation +deviceOrentation + 360) %360;
    }

    private static Size chooseOptimalSize(Size[] choices, int width,int height){
      //Get the optimal res and size
        List<Size> bigEnoughRes = new ArrayList<Size>();
        for(Size s : choices){
            if(s.getHeight() == s.getWidth() * height / width  &&
                    s.getWidth() >= width && s.getHeight() >= height){
                bigEnoughRes.add(s);
            }
        }

        if(bigEnoughRes.size() > 0 ){
            return Collections.min(bigEnoughRes,new CompareSizeByArea());
        }
        else{
            return choices[0];
        }
    }

    private void transformImage(int width, int height){
        //fix rotation bug
        if(mPreviewSize == null || textureView == null){
            return;
        }

        Matrix matrix = new Matrix();
        int rotation  = getWindowManager().getDefaultDisplay().getRotation();
        RectF texttureRectF = new RectF(0,0,width,height);
        RectF previewRectF = new RectF(0,0,mPreviewSize.getHeight(),mPreviewSize.getWidth());
        float centerX = texttureRectF.centerX();
        float centerY = texttureRectF.centerY();

        if(rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270){
            previewRectF.offset(centerX - previewRectF.centerX(),
                    centerY - previewRectF.centerY());

            matrix.setRectToRect(texttureRectF,previewRectF,Matrix.ScaleToFit.FILL);
            float scale = Math.max((float)width / mPreviewSize.getWidth(),
                    (float)height / mPreviewSize.getHeight());
            matrix.postScale(scale,scale,centerX,centerY);
            matrix.postRotate(90 * (rotation - 2), centerX,centerY);
        }

        textureView.setTransform(matrix);

    }

    private void process(Bitmap frame) {
        //implement histogram pros

        createPaletteAsync(frame);
        //createPaletteSync(frame);
        updateTextViews();
    }

    private void updateTextViews(){
        //update the UI
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                //update the UI here
                test1.setBackgroundColor(vibrantSwatch.getRgb());

            }
        });
    }


    public void createPaletteAsync(Bitmap bitmap) {
        Palette.from(bitmap).maximumColorCount(32).generate(new Palette.PaletteAsyncListener() {
            public void onGenerated(Palette p) {
                vibrantSwatch = p.getVibrantSwatch();
                lightVibrantSwatch = p.getLightVibrantSwatch();
                darkVibrantSwatch = p.getDarkVibrantSwatch();
                mutedSwatch = p.getMutedSwatch();
                lightMutedSwatch = p.getLightMutedSwatch();
                darkMutedSwatch = p.getDarkMutedSwatch();
            }
        });
    }

    public void createPaletteSync(Bitmap bitmap) {
        Palette p = Palette.from(bitmap).maximumColorCount(32).generate();

        vibrantSwatch = p.getVibrantSwatch();
        lightVibrantSwatch = p.getLightVibrantSwatch();
        darkVibrantSwatch = p.getDarkVibrantSwatch();
        mutedSwatch = p.getMutedSwatch();
        lightMutedSwatch = p.getLightMutedSwatch();
        darkMutedSwatch = p.getDarkMutedSwatch();
    }
}
