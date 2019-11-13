package com.example.digitalizedphotobook;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.digitalizedphotobook.classes.NativeClass;
import com.example.digitalizedphotobook.classes.Quadrilateral;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static android.graphics.Bitmap.createBitmap;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2GRAY;
import static org.opencv.imgproc.Imgproc.cvtColor;


public class AdjustmentActivity extends AppCompatActivity {
    private static final String TAG = "AdjustmentActivity123";
    private ImageView ivBack, ivCrop, ivConfirm, ivRotateLeft, ivRotateRight;
    public static ImageView ivResult;
    private FrameLayout frmHolder;
    private PolygonView polygonView;
    private File mFile, mFile2;
    private String imagePath;
    private NativeClass nativeClass;
    private double gammaValue = 1.0;
    private Bitmap bmp, newBmp;
    private Mat mat;
    private Quadrilateral quad;
    private boolean isFourPointed = false;
    private boolean isCropped = false;
    private int reqCode;
    private Map<Integer, PointF> points;
    private boolean isEditing = false;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i("OpenCV", "OpenCV loaded successfully");
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public AdjustmentActivity() {
    }

    private void showToast(final String text) {
        Toast toast = Toast.makeText(AdjustmentActivity.this, text, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP, 0, 30);
        toast.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adjustment);

        if (!OpenCVLoader.initDebug()) {
            return;
        }

        int permissionCheck = ContextCompat.checkSelfPermission(AdjustmentActivity.this,
                Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck != PermissionChecker.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission Not Granted");
            ActivityCompat.requestPermissions(AdjustmentActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
            return;
        }

        imagePath = new File(getExternalFilesDir("Temp"), "temp.jpg").getAbsolutePath();
        reqCode = getIntent().getIntExtra("reqCode", -1);
        mFile = new File(imagePath);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = false;
        bmp = BitmapFactory.decodeFile(mFile.getAbsolutePath(), options);
        if (bmp != null) {
            initializeElement();
        } else {
            showToast("Retake Photo");
            finish();
        }

    }
    private void initializeElement(){
        nativeClass = new NativeClass();
        ivBack = findViewById(R.id.ivBack);
        ivRotateLeft = findViewById(R.id.ivRotateLeft);
        ivRotateRight = findViewById(R.id.ivRotateRight);
        ivCrop = findViewById(R.id.ivCrop);
        ivConfirm = findViewById(R.id.ivConfirm);
        ivResult = findViewById(R.id.ivResult);
        polygonView = findViewById(R.id.polygonView);
        frmHolder = findViewById(R.id.holderImageCrop);

        Observable.fromCallable(() -> {
            setImageRotation();
            return false;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    frmHolder.post(this::initializeCropping);
                    ivRotateLeft.setOnClickListener(btnRotateLeft);
                    ivRotateRight.setOnClickListener(btnRotateRight);
                    ivCrop.setOnClickListener(btnCropToFit);
                    ivConfirm.setOnClickListener(btnConfirmClick);
                    ivBack.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            alertDialog();
                        }
                    });
                });
    }

    private void doImageProcessing(Bitmap src) {
        Mat tempDisplayMat = new Mat();
        Utils.bitmapToMat(src, tempDisplayMat);
        Mat tempClone = tempDisplayMat.clone();
        gammaValue = autoGammaValue(tempClone);

        doGammaCorrection(tempDisplayMat);
//        SimplestColorBalance(tempDisplayMat, 5);
        Utils.matToBitmap(tempDisplayMat, newBmp);
        ivResult.setImageBitmap(newBmp);
    }

    private void initializeCropping() {
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bmp, frmHolder.getWidth(), frmHolder.getHeight(), true);
        ivResult.setImageBitmap(scaledBitmap);
        mat = new Mat(scaledBitmap.getWidth(),scaledBitmap.getHeight(),CvType.CV_8UC4);
        Bitmap tempBitmap = ((BitmapDrawable) ivResult.getDrawable()).getBitmap();
        Utils.bitmapToMat(tempBitmap, mat);
        findContours(mat);
        Map<Integer, PointF> pointFs = null;
        try {
            pointFs = getEdgePoints(tempBitmap);
            polygonView.setPoints(pointFs);
            polygonView.setVisibility(View.VISIBLE);

            int padding = (int) getResources().getDimension(R.dimen.scanPadding);

            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(tempBitmap.getWidth() + 2 * padding, tempBitmap.getHeight() + 2 * padding);
            layoutParams.gravity = Gravity.CENTER;

            polygonView.setLayoutParams(layoutParams);
            polygonView.setPointColor(getResources().getColor(R.color.blue));
            doImageProcessing(scaledBitmap);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setImageRotation() {
        Bitmap tempBitmap = bmp.copy(bmp.getConfig(),true);
        for (int i = 1; i <= 4; i++) {
            MatOfPoint2f point2f = nativeClass.getPoint(tempBitmap);
            if (point2f == null) {
                bmp = rotateBitmap(tempBitmap, 90);
            } else {
                bmp = tempBitmap.copy(bmp.getConfig(), true);
                break;
            }
        }
    }

    public Bitmap rotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        bmp = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        return bmp;
    }

    private View.OnClickListener btnRotateLeft = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

        }
    };
    private View.OnClickListener btnRotateRight = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ivResult.setRotation(ivResult.getRotation() + 90);
            polygonView.setRotation(polygonView.getRotation() + 90);
            float scaledRatio = Float.parseFloat(Integer.toString(ivResult.getWidth()))
                    / Float.parseFloat(Integer.toString(ivResult.getHeight()));
            if (ivResult.getRotation() == 360 || polygonView.getRotation() == 360) {
                ivResult.setRotation(0);
                polygonView.setRotation(0);
            }
            if (ivResult.getRotation() == 90 || ivResult.getRotation() == -90 || ivResult.getRotation() == 270 || ivResult.getRotation() == -270) {
                ivResult.setScaleX(scaledRatio);
                ivResult.setScaleY(scaledRatio);
                polygonView.setScaleX(scaledRatio);
                polygonView.setScaleY(scaledRatio);
            } else {
                ivResult.setScaleX(1.0f);
                ivResult.setScaleY(1.0f);
                polygonView.setScaleX(1.0f);
                polygonView.setScaleY(1.0f);
            }
        }
    };
    private View.OnClickListener btnCropToFit = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!isFourPointed) {
                Map<Integer, PointF> pointFs = getOutlinePoints(newBmp);
                polygonView.setPoints(pointFs);
            } else {
                if (isCropped) {
                    // Undo Crop Here
                    ivCrop.setColorFilter(ContextCompat.getColor(AdjustmentActivity.this, R.color.blue), PorterDuff.Mode.SRC_IN);
                    Map<Integer, PointF> pointFs = getOutlinePoints(newBmp);
                    polygonView.setPoints(pointFs);
                    polygonView.invalidate();
                    polygonView.setVisibility(View.VISIBLE);
                    isCropped = false;
                } else {
                    // Auto Crop Here
                    ivCrop.setColorFilter(ContextCompat.getColor(AdjustmentActivity.this, R.color.color_white), PorterDuff.Mode.SRC_IN);
                    Map<Integer, PointF> pointFs = getEdgePoints(newBmp);
                    polygonView.setPoints(pointFs);
                    polygonView.invalidate();
                    polygonView.setVisibility(View.VISIBLE);
                    isCropped = true;
                }
            }
        }
    };
    private View.OnClickListener btnConfirmClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            points = polygonView.getPoints();
            Point[] pointArr = new Point[4];
            for (int i = 0; i < points.size(); i++) {
                pointArr[i] = new Point((double) points.get(i).x, (double) points.get(i).y);
            }
            if (polygonView.isValidShape(points)) {
                Mat dest = perspectiveChange(mat, pointArr);
                Matrix matrix = new Matrix();
                matrix.postRotate(ivResult.getRotation());
                Bitmap tfmBmp = createBitmap(dest.width(), dest.height(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(dest, tfmBmp);
                Bitmap rotatedBmp = createBitmap(tfmBmp, 0, 0, tfmBmp.getWidth(), tfmBmp.getHeight(), matrix, true);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                rotatedBmp.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                byte[] bytes = stream.toByteArray();
                mFile2 = new File(getExternalFilesDir("Temp"), "temp2.jpg");
                try {
                    mFile2.createNewFile();
                    FileOutputStream fileOutputStream = new FileOutputStream(mFile2);
                    fileOutputStream.write(bytes);
                    fileOutputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
//            ivConfirm.setColorFilter(ContextCompat.getColor(AdjustmentActivity.this, R.color.color_white), PorterDuff.Mode.SRC_IN);
                isEditing = true;
                Intent intent = new Intent(AdjustmentActivity.this, ResultActivity.class);
                intent.putExtra("croppedPoints", mFile2.getAbsolutePath());
                float scaledRatio = Float.parseFloat(Integer.toString(ivResult.getWidth()))
                        / Float.parseFloat(Integer.toString(ivResult.getHeight()));
                intent.putExtra("scaledRatio", scaledRatio);
                if (ivResult.getRotation() == 90 || ivResult.getRotation() == -90 || ivResult.getRotation() == 270 || ivResult.getRotation() == -270) {
                    intent.putExtra("isRotated", true);
                }
                startActivity(intent);

            } else {
                showToast("Invalid Shape!");
            }
        }
    };

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private Mat otsuAutoCanny(Mat src) {
        Mat newSrc = new Mat();
        double otsu_thresh_val = Imgproc.threshold(src, new Mat(), 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        double high_thresh_val = otsu_thresh_val,
                lower_thresh_val = otsu_thresh_val * 0.5;
        Imgproc.Canny(src, newSrc, lower_thresh_val, high_thresh_val);
        return newSrc;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
//            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
//            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        if (isEditing) {
            if (points != null) {
                polygonView.setPoints(points);
            } else {
                Bitmap returnBmp = ((BitmapDrawable) ivResult.getDrawable()).getBitmap();
                polygonView.setPoints(getOutlinePoints(returnBmp));
            }
        }
    }

    private Mat perspectiveChange(Mat src, Point[] points) {

        Point bl = points[0];
        Point br = points[1];
        Point tl = points[2];
        Point tr = points[3];
        double widthA = Math.sqrt(Math.pow(br.x - bl.x, 2) + Math.pow(br.y - bl.y, 2));
        double widthB = Math.sqrt(Math.pow(tr.x - tl.x, 2) + Math.pow(tr.y - tl.y, 2));

        double dw = max(widthA, widthB);
        int maxWidth = Double.valueOf(dw).intValue();

        double heightA = Math.sqrt(Math.pow(tr.x - br.x, 2) + Math.pow(tr.y - br.y, 2));
        double heightB = Math.sqrt(Math.pow(tl.x - bl.x, 2) + Math.pow(tl.y - bl.y, 2));

        double dh = max(heightA, heightB);
        int maxHeight = Double.valueOf(dh).intValue();

        Mat destImage = new Mat(maxHeight, maxWidth, CvType.CV_8UC4);
        Mat srcMat = new Mat(4, 1, CvType.CV_32FC2);
        Mat dstMat = new Mat(4, 1, CvType.CV_32FC2);


        srcMat.put(0, 0, bl.x, bl.y, br.x, br.y, tr.x, tr.y, tl.x, tl.y);
        dstMat.put(0, 0, 0.0, 0.0, dw, 0.0, dw, dh, 0.0, dh);

        Mat transform = Imgproc.getPerspectiveTransform(srcMat, dstMat);
        Imgproc.warpPerspective(src, destImage, transform, destImage.size());
        return destImage;
    }

    private void alertDialog() {
        AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
        builder1.setMessage("Are you sure you want to discard this image?");
        builder1.setCancelable(true);

        builder1.setPositiveButton(
                "Discard",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        File file = new File(imagePath);
                        boolean deleted = file.delete();
                        if (!deleted) {
                            showToast("Error Discarding Image!");
                        }
                        dialog.cancel();
                        finish();
                    }
                });

        builder1.setNegativeButton(
                "Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        AlertDialog alert11 = builder1.create();
        alert11.show();
    }

    @Override
    public void onBackPressed() {
        alertDialog();
    }

    private Quadrilateral findContours(Mat src) {

        Size size = new Size(src.width(), src.height());
        Mat grayImage = new Mat(size, CvType.CV_8UC1);

        Imgproc.cvtColor(src, grayImage, Imgproc.COLOR_RGBA2GRAY, 1);

        Imgproc.GaussianBlur(grayImage, grayImage, new Size(5, 5), 0);
        Mat cannedImage = otsuAutoCanny(grayImage);
        Imgproc.morphologyEx(cannedImage, cannedImage, 3, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(7, 7)));
        Imgproc.morphologyEx(cannedImage, cannedImage, 4, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));
        ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(cannedImage, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        hierarchy.release();
        Collections.sort(contours, new Comparator<MatOfPoint>() {

            @Override
            public int compare(MatOfPoint lhs, MatOfPoint rhs) {
                return Double.valueOf(Imgproc.contourArea(rhs)).compareTo(Imgproc.contourArea(lhs));
            }
        });

        double maxVal = -1;
        int maxValIdx = 0;

        for (int contourIdx = 0; contourIdx < contours.size(); contourIdx++) {
            double contourArea = Imgproc.contourArea(contours.get(contourIdx));
            try {
                if (maxVal < contourArea) {
                    maxVal = contourArea;
                    maxValIdx = contourIdx;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            MatOfPoint2f c2f = new MatOfPoint2f(contours.get(maxValIdx).toArray());
            double peri = Imgproc.arcLength(c2f, true) * 0.02;
            MatOfPoint2f approx = new MatOfPoint2f();
            if (peri > 1) {
                Imgproc.approxPolyDP(c2f, approx, peri, true);

                MatOfPoint matOfPoint = new MatOfPoint(approx.toArray());
                Point[] points = approx.toArray();
//                Imgproc.drawContours(mat, contours, -1, new Scalar(0, 255, 0), 10);
                // select biggest 4 angles polygon
                if (matOfPoint.total() >= 4 & Math.abs(Imgproc.contourArea(matOfPoint)) > 1000) {
                    Point[] foundPoints = sortPoints(points);
                    isFourPointed = true;
                    isCropped = true;

                    quad = new Quadrilateral(contours.get(maxValIdx), foundPoints);
//                    for (Point point : quad.points) {
//                        Imgproc.circle(mat, point, 40, new Scalar(255, 0, 255), 20);
//                    }
                } else {
                    quad = null;
                }
                Utils.matToBitmap(mat, newBmp);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        grayImage.release();
        cannedImage.release();
        return quad;
    }

    private Point[] sortPoints(Point[] src) {

        ArrayList<Point> srcPoints = new ArrayList<>(Arrays.asList(src));

        Point[] result = {null, null, null, null};

        Comparator<Point> sumComparator = new Comparator<Point>() {
            @Override
            public int compare(Point lhs, Point rhs) {
                return Double.valueOf(lhs.y + lhs.x).compareTo(rhs.y + rhs.x);
            }
        };

        Comparator<Point> diffComparator = new Comparator<Point>() {

            @Override
            public int compare(Point lhs, Point rhs) {
                return Double.valueOf(lhs.y - lhs.x).compareTo(rhs.y - rhs.x);
            }
        };

        // top-left corner = minimal sum
        result[0] = Collections.min(srcPoints, sumComparator);

        // bottom-right corner = maximal sum
        result[2] = Collections.max(srcPoints, sumComparator);

        // top-right corner = minimal difference
        result[1] = Collections.min(srcPoints, diffComparator);

        // bottom-left corner = maximal difference
        result[3] = Collections.max(srcPoints, diffComparator);

        return result;
    }


    private Map<Integer, PointF> getEdgePoints(Bitmap tempBitmap) {
        List<PointF> pointFs = getContourEdgePoints(tempBitmap);
        Map<Integer, PointF> orderedPoints = orderedValidEdgePoints(tempBitmap, pointFs);
        return orderedPoints;
    }

    private List<PointF> getContourEdgePoints(Bitmap tempBitmap) {
        List<PointF> pointList = new ArrayList<>();
        if (quad != null) {
            Point[] quadPoints = quad.points;
            for (int i = 0; i < quadPoints.length; i++) {
                // Unable to obtain width and height of imageview so use ratio to hardcode points
                float x = Float.parseFloat(Double.toString(quadPoints[i].x));
                float y = Float.parseFloat(Double.toString(quadPoints[i].y));
                pointList.add(new PointF(x, y));
            }
        }
        return pointList;

    }

    private Map<Integer, PointF> getOutlinePoints(Bitmap tempBitmap) {
        Map<Integer, PointF> outlinePoints = new HashMap<>();
        outlinePoints.put(0, new PointF(0, 0));
        outlinePoints.put(1, new PointF((float) frmHolder.getWidth(), 0));
        outlinePoints.put(2, new PointF(0, (float) frmHolder.getHeight()));
        outlinePoints.put(3, new PointF((float) frmHolder.getWidth(), (float) frmHolder.getHeight()));
        return outlinePoints;

    }



    private Map<Integer, PointF> orderedValidEdgePoints(Bitmap tempBitmap, List<PointF> pointFs) {
        Map<Integer, PointF> orderedPoints = polygonView.getOrderedPoints(pointFs);
        if (!polygonView.isValidShape(orderedPoints)) {
            orderedPoints = getOutlinePoints(tempBitmap);
        }
        return orderedPoints;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 0: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    showToast("Permission not Granted!");
                }
            }
        }
    }

    private byte saturate(double val) {
        int iVal = (int) Math.round(val);
        iVal = iVal > 255 ? 255 : (iVal < 0 ? 0 : iVal);
        return (byte) iVal;
    }

    private double mean_pixel(Mat img) {
        if (img.channels() > 2) {
            cvtColor(img.clone(), img, COLOR_RGB2GRAY);
            return Core.mean(img).val[0];
        } else {
            return Core.mean(img).val[0];
        }
    }

    private double autoGammaValue(Mat src) {
        double max_pixel = 255;
        double middle_pixel = 128;
        double pixel_range = 256;
        double mean_l = mean_pixel(src);

        double gamma = log(middle_pixel / pixel_range) / log(mean_l / pixel_range); // Formula from ImageJ
        return gamma;
    }

    private void doGammaCorrection(Mat src) {
        //! [changing-contrast-brightness-gamma-correction]
        Mat lookUpTable = new Mat(1, 256, CvType.CV_8U);
        byte[] lookUpTableData = new byte[(int) (lookUpTable.total() * lookUpTable.channels())];
        for (int i = 0; i < lookUpTable.cols(); i++) {
            lookUpTableData[i] = saturate(Math.pow(i / 255.0, gammaValue) * 255.0);
        }
        lookUpTable.put(0, 0, lookUpTableData);
        Core.LUT(src, lookUpTable, src);
    }

    /**
     * Simplest Color Balance. Performs color balancing via histogram
     * normalization.
     *
     * @param img     input color or gray scale image
     * @param percent controls the percentage of pixels to clip to white and black. (normally, choose 1~10)
     * @return Balanced image in CvType.CV_32F
     */
    public static Mat SimplestColorBalance(Mat img, int percent) {
        if (percent <= 0)
            percent = 5;
        Imgproc.cvtColor(img, img, Imgproc.COLOR_RGB2RGBA);
//        img.convertTo(img, CvType.CV_32F);
        List<Mat> channels = new ArrayList<>();
        int rows = img.rows(); // number of rows of image
        int cols = img.cols(); // number of columns of image
        int chnls = img.channels(); //  number of channels of image
        double halfPercent = percent / 200.0;
        if (chnls == 3) Core.split(img, channels);
        else channels.add(img);
        List<Mat> results = new ArrayList<>();
        for (int i = 0; i < chnls; i++) {
            // find the low and high precentile values (based on the input percentile)
            Mat flat = new Mat();
            channels.get(i).reshape(1, 1).copyTo(flat);
            Core.sort(flat, flat, Core.SORT_ASCENDING);
            double lowVal = flat.get(0, (int) Math.floor(flat.cols() * halfPercent))[0];
            double topVal = flat.get(0, (int) Math.ceil(flat.cols() * (1.0 - halfPercent)))[0];
            // saturate below the low percentile and above the high percentile
            Mat channel = channels.get(i);
            for (int m = 0; m < rows; m++) {
                for (int n = 0; n < cols; n++) {
                    if (channel.get(m, n)[0] < lowVal) channel.put(m, n, lowVal);
                    if (channel.get(m, n)[0] > topVal) channel.put(m, n, topVal);
                }
            }
            Core.normalize(channel, channel, 0.0, 255.0 / 2, Core.NORM_MINMAX);
//            channel.convertTo(channel, CvType.CV_32F);
            results.add(channel);

        }
        Mat outval = new Mat();
        Core.merge(results, outval);
        Imgproc.cvtColor(outval, outval, Imgproc.COLOR_RGBA2RGB);
        return outval;
    }

}
