package com.flarejaven.example.jnithread.activity;

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.RelativeLayout;

import com.facepp.library.util.CameraMatrix;
import com.facepp.library.util.ConUtil;
import com.facepp.library.util.DialogUtil;
import com.facepp.library.util.ICamera;
import com.facepp.library.util.LandmarkConstants;
import com.facepp.library.util.Screen;
import com.facepp.library.util.SensorEventUtil;
import com.flarejaven.example.jnithread.NdkJniUtils;
import com.flarejaven.example.jnithread.R;
import com.flarejaven.example.jnithread.TextureMatrix;
import com.flarejaven.example.jnithread.util.FileUtils;
import com.flarejaven.example.jnithread.util.OpenGLHelper;
import com.flarejaven.example.jnithread.util.ZipUtils;
import com.megvii.facepp.sdk.Facepp;
import com.megvii.facepp.sdk.jni.NativeFaceppAPI;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity
        implements NdkJniUtils.OnDataComeListener, GLSurfaceView.Renderer, Camera.PreviewCallback, SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "MainActivity";
    private static final int INDEX_FOREGROUND_STICKER = 0;
    private static final int INDEX_BACKGROUND_STICKER = 1;

    private static final List<String> STICKERS_RES_LIST = new ArrayList<>();

    static {
        STICKERS_RES_LIST.add("rabbitRes");
    }

    // face tracker parameters
    private boolean is3DPose = true;
    private boolean is106Points = false;
    private boolean isBackCamera = false;
    private boolean isTiming = true; // 是否是定时去刷新界面;
    private int printTime = 31;
    private ICamera mICamera;
    private Camera mCamera;
    private DialogUtil mDialogUtil;

    private Facepp facepp;
    private int min_face_size = 200;
    private int detection_interval = 25;
    private ArrayList<HashMap<String, Integer>> cameraSize;
    private HashMap<String, Integer> resolutionMap;
    private SensorEventUtil sensorUtil;

    private boolean isSuccess = false;
    private float pitch;
    private float yaw;
    private float roll;
    private int angle;
    private int rotation = angle;

    private NdkJniUtils jni = new NdkJniUtils();
    private GLSurfaceView mGlSurfaceView;

//    private final Point bunnyFlvSize = new Point(208, 320);
//    private final Point vegetableFlvSize = new Point(368, 480);
    private volatile boolean isDrawing = false;

//    private Bitmap tempBmp;

    private volatile boolean reloadStickers = false;
    private int mStickerNum = 0;
    private SparseArray<Point> mStickerSizes = new SparseArray<>();
    private SparseArray<ByteBuffer> mBitmapBuffers = new SparseArray<>();
    private SparseArray<Integer> mTextureIds = new SparseArray<>();
    private SparseArray<TextureMatrix> mTextureMatrixs = new SparseArray<>();
    private int mCameraTextureId = OpenGLHelper.NO_TEXTURE;
    private SurfaceTexture mSurface;
    private CameraMatrix mCameraMatrix;

    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjMatrix = new float[16];
    private final float[] mVMatrix = new float[16];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Screen.initialize(this);
        setContentView(R.layout.activity_main);

        init();
    }

    private void init() {
        jni.setJNIEnv();
        jni.setOnMsgComeListener(this);

        if (android.os.Build.MODEL.equals("PLK-AL10")) {
            printTime = 50;
        }

        is3DPose = true;
        is106Points = false;
        isBackCamera = false;

        min_face_size = 200;
        detection_interval = 25;
        int cameraId = isBackCamera ? 0 : 1;
        cameraSize = ICamera.getCameraPreviewSize(cameraId);
        resolutionMap = cameraSize.get(0);
        Log.d(TAG, "init: resolutionMap = " + resolutionMap);

        facepp = new Facepp();

        sensorUtil = new SensorEventUtil(this);

        mGlSurfaceView = (GLSurfaceView) findViewById(R.id.glSurfaceView);
        mGlSurfaceView.setEGLContextClientVersion(2);
        mGlSurfaceView.setRenderer(this);
        mGlSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mICamera = new ICamera();
        mDialogUtil = new DialogUtil(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ConUtil.acquireWakeLock(this);
        mGlSurfaceView.onResume();
        mCamera = mICamera.openCamera(isBackCamera, this, resolutionMap);
        if (mCamera != null) {
            angle = 360 - mICamera.Angle;
            if (isBackCamera)
                angle = mICamera.Angle;

            RelativeLayout.LayoutParams layout_params = mICamera.getLayoutParam();
            mGlSurfaceView.setLayoutParams(layout_params);

            int width = mICamera.cameraWidth;
            int height = mICamera.cameraHeight;

            int left = 0;
            int top = 0;
            int right = width;
            int bottom = height;

            long [] algorithmInfo = NativeFaceppAPI.nativeGetAlgorithmInfo(ConUtil.getFileContent(this, com.facepp.library.R.raw.megviifacepp_0_4_5_model));
            long ability = algorithmInfo[2];
            Log.d(TAG, "onResume: ability = " + ability);

            String errorCode = facepp.init(this, ConUtil.getFileContent(this, com.facepp.library.R.raw.megviifacepp_0_4_5_model));
            Facepp.FaceppConfig faceppConfig = facepp.getFaceppConfig();
            faceppConfig.interval = detection_interval;
            faceppConfig.minFaceSize = min_face_size;
            faceppConfig.roi_left = left;
            faceppConfig.roi_top = top;
            faceppConfig.roi_right = right;
            faceppConfig.roi_bottom = bottom;
            faceppConfig.detectionMode = Facepp.FaceppConfig.DETECTION_MODE_TRACKING;
            facepp.setFaceppConfig(faceppConfig);
        } else {
            mDialogUtil.showDialog("打开相机失败");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGlSurfaceView.onPause();
        onStopThreadClick(null);

        ConUtil.releaseWakeLock();
        mICamera.closeCamera();
        mCamera = null;

        timeHandle.removeMessages(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        facepp.release();
        sensorUtil.release();
        timeHandle.removeCallbacksAndMessages(null);
    }

    public void onStartThreadClick(View view) {
        boolean isLoadSuccess = true;
        try {
            String assetPath = ZipUtils.unZip(view.getContext(), STICKERS_RES_LIST.get(0), ZipUtils.STICKERS_DIR, false);
            Log.i(TAG, "onStartThreadClick: assetPath = " + assetPath);
            // step1 read info.json
            File infoJson = new File(assetPath + File.separator + "info.json");
            if (!infoJson.exists()) {
                throw new IOException("info.json for " + STICKERS_RES_LIST.get(0) + " not found!");
            }
            String json = FileUtils.readToString(infoJson);
            JSONObject jsonObject = new JSONObject(json);
            JSONArray itemArray = jsonObject.getJSONArray("itemList");
            String[] stickerNames = new String[2];
            for (int i = 0; i < itemArray.length(); i++) {
                JSONObject item = itemArray.getJSONObject(i);
                String type = item.getString("type");
                String stickerName = item.getString("stickerName");
                int width = item.getInt("width");
                int height = item.getInt("height");

                int index = type.equals("F") ? INDEX_FOREGROUND_STICKER : INDEX_BACKGROUND_STICKER;
                Log.i(TAG, stickerName + " " + index + " " + width + " " + height);
                mStickerSizes.put(index, new Point(width, height));
                stickerNames[index] = assetPath + File.separator + stickerName;
            }

            jni.configStickerNames(stickerNames, itemArray.length());
        } catch (IOException e) {
            e.printStackTrace();
            isLoadSuccess = false;
        } catch (JSONException e) {
            e.printStackTrace();
            isLoadSuccess = false;
        }

        if (isLoadSuccess) {
            reloadStickers = true;
        }
    }

    // 配置贴纸信息
    private void loadStickers() {
        mStickerNum = mStickerSizes.size();

        for (int i = 0; i < mStickerNum; i++) {
            ByteBuffer bitmapBuffer = OpenGLHelper.createEmptyRGBABuffer(mStickerSizes.get(i));
            Integer textureId = OpenGLHelper.loadTexture(bitmapBuffer, mStickerSizes.get(i), OpenGLHelper.NO_TEXTURE);
            TextureMatrix textureMatrix = new TextureMatrix(textureId);
            mBitmapBuffers.put(i, bitmapBuffer);
            mTextureIds.put(i, textureId);
            mTextureMatrixs.put(i, textureMatrix);
        }

        if (mStickerNum == 2) {
            loadBackgroundSticker();
        }
    }

    private void resetData(boolean requestRender) {
        mStickerNum = 0;
//        mStickerSizes.clear();
//        mBitmapBuffers.clear();
//        mTextureIds.clear();
//        mTextureMatrixs.clear();

        if (requestRender) {
            mGlSurfaceView.requestRender();
        }
    }

    private void loadBackgroundSticker() {
        ByteBuffer bb = ByteBuffer.allocateDirect(4 * 3 * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer vertexBuffer = bb.asFloatBuffer();

        float[] vResult = calculateBgStickerVertexes(mStickerSizes.get(INDEX_BACKGROUND_STICKER));
        vertexBuffer.put(vResult);

        List<FloatBuffer> vList = new ArrayList<>(1);
        vList.add(vertexBuffer);
        mTextureMatrixs.get(INDEX_BACKGROUND_STICKER).setSquaerCoords(vList);
    }

    // 以屏幕宽度为准
    private float[] calculateBgStickerVertexes(Point stickerSize) {
        float[] result = new float[12];
        float width = Screen.mWidth;
        float height = width * ((float)stickerSize.y / (float)stickerSize.x);

        // calculate for aPoint
        result[0] = -1.0f;
        result[1] = -1.0f;
        result[2] = 0.0f;
        // calculate for bPoint
        result[3] = 1.0f;
        result[4] = -1.0f;
        result[5] = 0.0f;
        // calculate for dPoint
        result[6] = -1.0f;
        result[7] = 1 - ((Screen.drawHeight - height) / mICamera.cameraWidth) * 2;
        result[8] = 0.0f;
        // calculate for cPoint
        result[9] = 1.0f;
        result[10] = 1 - ((Screen.drawHeight - height) / mICamera.cameraWidth) * 2;
        result[11] = 0.0f;
//        Log.d(TAG, "calculateBgStickerVertexes: coordinate_top_y = " + result[7]);

        return result;
    }

    public void onStopThreadClick(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                jni.endThread();
                resetData(true);
            }
        }).start();
    }

    @Override
    public void onDataCome(final byte[] data, final int width, final int height, final int index) {
//        Log.d(TAG, "onDataCome: " + "width = " + width + " height = " + height + " index = " +index);
        if (!isDrawing) {
            OpenGLHelper.fillData(mBitmapBuffers.get(index), data);
        }

//        mGlSurfaceView.requestRender();
    }

    @Override
    public void onPreviewFrame(final byte[] imgData, Camera camera) {
        if (isSuccess) {
            return;
        }
        isSuccess = true;

        int width = mICamera.cameraWidth;
        int height = mICamera.cameraHeight;

        int orientation = sensorUtil.orientation;
        if (orientation == 0) {
            rotation = angle;
        } else if (orientation == 1) {
            rotation = 0;
        } else if (orientation == 2) {
            rotation = 180;
        } else if (orientation == 3) {
            rotation = 360 - angle;
        }

        setConfig(rotation);

        final Facepp.Face[] faces = facepp.detect(imgData, width, height, Facepp.IMAGEMODE_NV21); // PreviewCallback default format is NV21

        if (faces != null) {
            List<FloatBuffer> vertextBuffers = new ArrayList<>();
//                    Log.d(TAG, "run: faces_size = " + faces.length);

            if (faces.length >= 0 && mTextureMatrixs.get(INDEX_FOREGROUND_STICKER) != null) {
                for (Facepp.Face face : faces) {
                    if (is106Points) {
                        facepp.getLandmark(face, Facepp.FPP_GET_LANDMARK106);
                    } else {
                        facepp.getLandmark(face, Facepp.FPP_GET_LANDMARK81);
                    }

                    if (is3DPose) {
                        facepp.get3DPose(face);
                    }

                    pitch = face.pitch;
                    yaw = face.yaw;
                    roll = face.roll;

                    if (orientation == 1 || orientation == 2) {
                        width = mICamera.cameraHeight;
                        height = mICamera.cameraWidth;
                    }

                    double real_roll = roll + Math.PI + (rotation / 180.0f) * Math.PI;
                    while (real_roll > 2 * Math.PI)
                        real_roll -= 2 * Math.PI;
                    roll = (float)(real_roll - Math.PI);
                    float rad = (float) (Math.PI - Math.abs(roll));
                    rad = roll > 0 ? rad : -rad;

                    PointF pivotDown = new PointF(face.points[LandmarkConstants.MG_MOUTH_UPPER_LIP_BOTTOM].x,
                            face.points[LandmarkConstants.MG_MOUTH_UPPER_LIP_BOTTOM].y);
                    float targetWidth = distance(face.points[LandmarkConstants.MG_CONTOUR_LEFT2].x, face.points[LandmarkConstants.MG_CONTOUR_LEFT2].y,
                            face.points[LandmarkConstants.MG_CONTOUR_RIGHT2].x, face.points[LandmarkConstants.MG_CONTOUR_RIGHT2].y);
                    targetWidth *= 1.2f; // TODO size coefficient of correction
                    float targetHeight = targetWidth * ((float)mStickerSizes.get(INDEX_FOREGROUND_STICKER).y / (float)mStickerSizes.get(INDEX_FOREGROUND_STICKER).x);
//                    Log.d(TAG, "run: height = " + targetHeight + " width = " + targetWidth);

                    float[] aPoint = new float[] {pivotDown.x - targetWidth / 2, pivotDown.y}; // left_bottom
                    float[] bPoint = new float[] {pivotDown.x + targetWidth / 2, pivotDown.y}; // right_bottom
                    float[] cPoint = new float[] {bPoint[0], pivotDown.y - targetHeight}; // right_top
                    float[] dPoint = new float[] {aPoint[0], pivotDown.y - targetHeight}; // left_top

                    android.graphics.Matrix matrix = new android.graphics.Matrix();
                    matrix.postRotate((float) Math.toDegrees(rad), pivotDown.x, pivotDown.y);
                    matrix.mapPoints(aPoint);
                    matrix.mapPoints(bPoint);
                    matrix.mapPoints(cPoint);
                    matrix.mapPoints(dPoint);

                    ByteBuffer bb = ByteBuffer.allocateDirect(4 * 3 * 4);
                    bb.order(ByteOrder.nativeOrder());
                    FloatBuffer vertexBuffer = bb.asFloatBuffer();
                    vertexBuffer.put(screenCoorToGLCoor(aPoint, height, width, orientation));
                    vertexBuffer.put(screenCoorToGLCoor(bPoint, height, width, orientation));
                    vertexBuffer.put(screenCoorToGLCoor(dPoint, height, width, orientation));
                    vertexBuffer.put(screenCoorToGLCoor(cPoint, height, width, orientation));
                    vertexBuffer.position(0);

                    vertextBuffers.add(vertexBuffer);
                }
                mTextureMatrixs.get(INDEX_FOREGROUND_STICKER).setSquaerCoords(vertextBuffers);
            } else {
                pitch = 0.0f;
                yaw = 0.0f;
                roll = 0.0f;
            }
        }
        isSuccess = false;
        if (!isTiming) {
            timeHandle.sendEmptyMessage(1);
        }
    }

    private float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    private float[] screenCoorToGLCoor(float[] point, int height, int width, int orientation) {
        float x = (point[0] / height) * 2 - 1;
        if (isBackCamera)
            x = -x;
        float y = 1 - (point[1] / width) * 2;

        float[] pointf = new float[] { x, y, 0.0f };
        if (orientation == 1)
            pointf = new float[] { -y, x, 0.0f };
        if (orientation == 2)
            pointf = new float[] { y, -x, 0.0f };
        if (orientation == 3)
            pointf = new float[] { -x, -y, 0.0f };
        return pointf;
    }

    private float[] screenCoorToGLCoor(PointF point, int height, int width, int orientation) {
        return screenCoorToGLCoor(new float[] {point.x, point.y}, height, width, orientation);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);

        mCameraTextureId = OpenGLHelper.createTextureID();
        mSurface = new SurfaceTexture(mCameraTextureId);
        mSurface.setOnFrameAvailableListener(this);
        mCameraMatrix = new CameraMatrix(mCameraTextureId);
        mICamera.startPreview(mSurface);
        mICamera.actionDetect(this);
        if (isTiming) {
            timeHandle.sendEmptyMessageDelayed(0, printTime);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        Matrix.frustumM(mProjMatrix, 0, -1, 1, -1, 1, 3, 7);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        float[] mtx = new float[16];
        mSurface.getTransformMatrix(mtx);
        mCameraMatrix.draw(mtx);

        Matrix.setLookAtM(mVMatrix, 0, 0, 0, -3, 0f, 0f, 0f, 0f, 1f, 0f);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mVMatrix, 0);

        if (reloadStickers) {
            reloadStickers = false;
            loadStickers();
            jni.startThread();
        }

        isDrawing = true;
        for (int i = mStickerNum - 1; i >= 0; i--) {
            OpenGLHelper.loadTexture(mBitmapBuffers.get(i),
                    mStickerSizes.get(i), mTextureIds.get(i));
            mTextureMatrixs.get(i).draw(mMVPMatrix);
            if (mStickerNum == 0) {
                mBitmapBuffers.clear();
                mStickerSizes.clear();
                mTextureIds.clear();
                mTextureMatrixs.clear();
            }
        }
        isDrawing = false;

        mSurface.updateTexImage();
    }

    private Handler timeHandle = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    mGlSurfaceView.requestRender();// 发送去绘制照相机不断去回调
                    timeHandle.sendEmptyMessageDelayed(0, printTime);
                    break;
                case 1:
                    mGlSurfaceView.requestRender();// 发送去绘制照相机不断去回调
                    break;
            }
        }
    };

    private void setConfig(int rotation) {
        Facepp.FaceppConfig faceppConfig = facepp.getFaceppConfig();
        if (faceppConfig.rotation != rotation) {
            faceppConfig.rotation = rotation;
            facepp.setFaceppConfig(faceppConfig);
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {

    }
}
