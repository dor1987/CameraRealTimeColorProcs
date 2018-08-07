package ashush.pdactech;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;


public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERM_RES = 0;



    //Views arrays
    List<TextView> percemtTextViewArray,colorCodeTextViewArray;


    private TextureView mTextureView;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            setupCamera(i,i1);
            //fix camera rotation bug
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

    //Will return CameraDevice when its available
    private CameraDevice.StateCallback mCamStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
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

    //Creating background thread
    private HandlerThread mBackGroundHandlerThread;
    private Handler mBackGroundHandler;


    private String mCameraId;
    private Size mPreviewSize;
    private CaptureRequest.Builder mCaptureRequestBuilder;

    private ImageReader mImageReader;

    //Get frame from preview when it's available
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                //Converting to JPEG
                byte[] jpegData = ImageUtils.imageToByteArray(image);
                Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
                process(bitmap);
                image.close();
            }
        }
    };


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
        initTextViews();

        //Defining landscape orientation.
        setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);
    }

    private void initTextViews() {
        //Init all the views
        percemtTextViewArray = new ArrayList<TextView>();
        colorCodeTextViewArray = new ArrayList<TextView>();

        percemtTextViewArray.add((TextView) findViewById(R.id.textViewColorNum1Percent));
        percemtTextViewArray.add((TextView) findViewById(R.id.textViewColorNum2Percent));
        percemtTextViewArray.add((TextView) findViewById(R.id.textViewColorNum3Percent));
        percemtTextViewArray.add((TextView) findViewById(R.id.textViewColorNum4Percent));
        percemtTextViewArray.add((TextView) findViewById(R.id.textViewColorNum5Percent));


        colorCodeTextViewArray.add((TextView) findViewById(R.id.textViewColorNum1Code));
        colorCodeTextViewArray.add((TextView) findViewById(R.id.textViewColorNum2Code));
        colorCodeTextViewArray.add((TextView) findViewById(R.id.textViewColorNum3Code));
        colorCodeTextViewArray.add((TextView) findViewById(R.id.textViewColorNum4Code));
        colorCodeTextViewArray.add((TextView) findViewById(R.id.textViewColorNum5Code));


        mTextureView = (TextureView) findViewById(R.id.cameraTextureView);
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

        //Check if textureView is available
        if(mTextureView.isAvailable()){
            setupCamera(mTextureView.getWidth(),mTextureView.getHeight());
            //Fix rotation bug
            transformImage(mTextureView.getWidth(),mTextureView.getHeight());
            connectCamera();
        }

        else{
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackGroundThread();
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
            //check permission for Marshmallow and up
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
        //Start the preview display on the screen
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
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
        //Fix rotation bug
        //Friend helped me out on this one
        if(mPreviewSize == null || mTextureView == null){
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

        mTextureView.setTransform(matrix);

    }

    private void process(Bitmap frame) {
        //Source: https://developer.android.com/reference/android/support/v7/graphics/Palette
        //Google Palette Lib - used to get the dominant colors of an image and there population
        createPaletteAsync(frame);
    }

    private void updateTextViews(final List<Palette.Swatch> swatchArrayList, final int totalAmountOfPixels){
        //update the UI
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                //update all the textviews
                for(int i = 0; i< swatchArrayList.size() && i < 5;i++){
                    double tempPercentCover = ((double)swatchArrayList.get(i).getPopulation()/totalAmountOfPixels)*100;
                    int colorOfCurrentSwatch = swatchArrayList.get(i).getRgb();
                    percemtTextViewArray.get(i).setBackgroundColor(colorOfCurrentSwatch);
                    percemtTextViewArray.get(i).setText("" +String.format("%.2f", tempPercentCover)+"%");
                    colorCodeTextViewArray.get(i).setText(getRedGreenBlueAsString(colorOfCurrentSwatch));
                }
            }
        });
    }

    private String getRedGreenBlueAsString(int colorAsInt){
        //Source: https://developer.android.com/reference/android/graphics/Color
        //Help getting the string format needed
        int red = Color.red(colorAsInt);
        int green = Color.green(colorAsInt);
        int blue = Color.blue(colorAsInt);

        return "R:"+red+" G:"+green+" B:"+blue;
    }

    private List<Palette.Swatch> orderSwatchByPopulation(List<Palette.Swatch> swatchArrayList) {
        Collections.sort(swatchArrayList, new SwatchComperator());
        return swatchArrayList;
    }

    public class SwatchComperator implements Comparator<Palette.Swatch>
    {
        @Override
        public int compare(Palette.Swatch swatch1, Palette.Swatch swatch2) {
                if(swatch1 == null && swatch2 != null)
                    return 1;
                else if(swatch1 != null && swatch2 == null)
                    return -1;
                else if(swatch1 == null && swatch2 == null)
                    return 0;
                else
                    return (swatch2.getPopulation()-swatch1.getPopulation());
        }
    }

    public void createPaletteAsync(final Bitmap bitmap) {
        Palette.from(bitmap).maximumColorCount(10).generate(new Palette.PaletteAsyncListener() {
            public void onGenerated(Palette p) {

                List<Palette.Swatch> swatchArrayList = new ArrayList<Palette.Swatch>();
                swatchArrayList.addAll(p.getSwatches());

                List<Palette.Swatch> sortedSwatchArrayList = new ArrayList<Palette.Swatch>();
                for(int i = 0; i<swatchArrayList.size() ; i++){
                    sortedSwatchArrayList.add(swatchArrayList.get(i));
                }

                updateTextViews(orderSwatchByPopulation(sortedSwatchArrayList),calcTotalAmountOfPixels(bitmap));
            }
        });
    }

    private int calcTotalAmountOfPixels(Bitmap frame){
        return (frame.getWidth()*frame.getHeight());
    }
}

