package com.example.digitalizedphotobook;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.digitalizedphotobook.adapters.FilterAdapter;
import com.example.digitalizedphotobook.classes.Filter;
import com.github.chrisbanes.photoview.PhotoView;
import com.squareup.picasso.Picasso;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static java.lang.Math.log;
import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.core.CvType.CV_8UC3;
import static org.opencv.core.CvType.CV_8UC4;
import static org.opencv.imgproc.Imgproc.COLOR_BGR2RGB;
import static org.opencv.imgproc.Imgproc.COLOR_GRAY2RGB;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2BGR;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2GRAY;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2RGBA;
import static org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY;
import static org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB;
import static org.opencv.imgproc.Imgproc.cvtColor;

public class ResultActivity extends AppCompatActivity {
    public static final String TAG = "ResultActivity";
    public ImageView ivFilters, ivBack, ivSave, ivMore, ivRotate;
    private PhotoView ivResult;
    private TextView tvBrightness, tvContrast, tvReset;
    private LinearLayout lightSettings, linlay1, filterSettings;
    private SeekBar seekBarBrightness, seekBarContrast;
    private float scaledRatio;
    private String imagePath;
    private Toolbar toolbar;
    private Bitmap bitmap, newBitMap;
    private View mView;
    private File mFile;
    public Mat mat, newMat;
    private boolean colorMode = false;
    private boolean filterMode = true;
    private double colorGain = 1.5;       // contrast
    private double colorBias = 0;         // bright
    private int colorThresh = 110;
    private double gammaValue = 1.0;
    private boolean isFilterExtended = false;
    private boolean isLightExtended = false;
    private boolean isRotated = false;
    private int iBrightness = 50;
    private double dContrast = 1.0;
    private RecyclerView rvFilter;
    private RecyclerView.LayoutManager layManager;
    ArrayList<Filter> filterArr = new ArrayList<Filter>();
    FilterAdapter adapter;

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

    private void showToast(final String text) {
        Toast toast = Toast.makeText(ResultActivity.this, text, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP, 0, 30);
        toast.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);


        toolbar = (Toolbar) findViewById(R.id.include);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("");

        ivBack = findViewById(R.id.ivBack);
        ivFilters = findViewById(R.id.ivFilters);
        ivResult = findViewById(R.id.ivResult);
        ivSave = findViewById(R.id.ivConfirm);
        ivRotate = findViewById(R.id.ivRotate);
        ivMore = findViewById(R.id.ivMore);
        ivFilters = findViewById(R.id.ivFilters);
        rvFilter = findViewById(R.id.rvFilters);
        tvContrast = findViewById(R.id.tvContrast);
        tvBrightness = findViewById(R.id.tvBrightness);
        tvReset = findViewById(R.id.tvReset);
        seekBarBrightness = findViewById(R.id.sbBrightness);
        seekBarContrast = findViewById(R.id.sbContrast);
        lightSettings = findViewById(R.id.lightSettings);
        filterSettings = findViewById(R.id.filterSettings);
        linlay1 = findViewById(R.id.linlay1);
        mView = findViewById(R.id.clickView);

        if (!OpenCVLoader.initDebug()) {
            return;
        }
        int permissionCheck = ContextCompat.checkSelfPermission(ResultActivity.this,
                Manifest.permission.READ_EXTERNAL_STORAGE);

        if (permissionCheck != PermissionChecker.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission Not Granted");
            ActivityCompat.requestPermissions(ResultActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
            return;
        }

        rvFilter.setHasFixedSize(true);
        layManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvFilter.setLayoutManager(layManager);
        adapter = new FilterAdapter(filterArr);

        imagePath = getIntent().getStringExtra("croppedPoints");
        isRotated = getIntent().getBooleanExtra("isRotated", false);
        scaledRatio = getIntent().getFloatExtra("scaledRatio", 0.0f);

        mFile = new File(imagePath);
        Log.i(TAG, "ABSOLUTE PATH" + mFile.getAbsolutePath());
        setPic(mFile.getAbsolutePath());

        ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        ivFilters.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                if (!isLightExtended) {
//                    if (!isFilterExtended) {
//                        filterSettings.setVisibility(View.VISIBLE);
//                        mView.setVisibility(View.VISIBLE);
//                        mView.animate().translationY(filterSettings.getHeight() * -1);
//                        filterSettings.animate().translationY(filterSettings.getHeight() * -1);
//                        isFilterExtended = true;
//                    } else {
//                        mView.setVisibility(View.GONE);
//                        filterSettings.animate().translationY(filterSettings.getHeight() + linlay1.getHeight());
//                        isFilterExtended = false;
//                    }
//                } else {
//                    mView.setVisibility(View.GONE);
//                    lightSettings.animate().translationY(lightSettings.getHeight() + linlay1.getHeight());
//                    isLightExtended = false;
//                    filterSettings.setVisibility(View.VISIBLE);
//                    mView.setVisibility(View.VISIBLE);
//                    mView.animate().translationY(filterSettings.getHeight() * -1);
//                    filterSettings.animate().translationY(filterSettings.getHeight() * -1);
//                    isFilterExtended = true;
//                }
            }
        });
        //Expand the Light Settings
        ivMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isFilterExtended) {
                    if (!isLightExtended) {
                        lightSettings.setVisibility(View.VISIBLE);
                        mView.setVisibility(View.VISIBLE);
                        mView.animate().translationY(lightSettings.getHeight() * -1);
                        lightSettings.animate().translationY(lightSettings.getHeight() * -1);
                        isLightExtended = true;
                    } else {
                        mView.setVisibility(View.GONE);
                        lightSettings.animate().translationY(lightSettings.getHeight() + linlay1.getHeight());
                        isLightExtended = false;
                    }
                } else {
                    mView.setVisibility(View.GONE);
                    filterSettings.animate().translationY(filterSettings.getHeight() + linlay1.getHeight());
                    isFilterExtended = false;
                    lightSettings.setVisibility(View.VISIBLE);
                    mView.setVisibility(View.VISIBLE);
                    mView.animate().translationY(lightSettings.getHeight() * -1);
                    lightSettings.animate().translationY(lightSettings.getHeight() * -1);
                    isLightExtended = true;
                }
            }
        });

        newBitMap = ((BitmapDrawable) ivResult.getDrawable()).getBitmap();
        newMat = new Mat(mat.rows(), mat.cols(), mat.type());
        Utils.bitmapToMat(newBitMap, newMat);
        ArrayList<Bitmap> filterBmpArr = new ArrayList<>();
        String[] filterNames = {"Autumn", "Bone", "Jet", "Winter", "Rainbow", "Ocean", "Summer", "Spring", "Cool", "HSV", "Pink", "Hot"};


        for (int i = 0; i < 12; i++) {
            Mat filterMat = new Mat(newMat.rows(), newMat.cols(), CvType.CV_8UC1);
            cvtColor(newMat, filterMat, Imgproc.COLOR_RGB2GRAY, 1);
            Imgproc.applyColorMap(filterMat, filterMat, i);
            Imgproc.resize(filterMat, filterMat, new Size(90, 90));
            Bitmap bitmap = Bitmap.createBitmap(filterMat.width(), filterMat.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(filterMat, bitmap);
            filterBmpArr.add(bitmap);
            filterMat.release();
        }

        for (int i = 0; i < filterBmpArr.size(); i++) {
            Filter filter = new Filter(filterNames[i], filterBmpArr.get(i));
            filterArr.add(filter);
        }

        rvFilter.setAdapter(adapter);

        mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLightExtended) {
                    lightSettings.animate().translationY(lightSettings.getHeight() + linlay1.getHeight());
                    isLightExtended = false;
                }
                if (isFilterExtended) {
                    filterSettings.animate().translationY(filterSettings.getHeight() + linlay1.getHeight());
                    isFilterExtended = false;

                }
                mView.setVisibility(View.GONE);
            }
        });

        ivRotate.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int eid = event.getAction();
                switch (eid) {
                    case MotionEvent.ACTION_DOWN:
                        ivRotate.setColorFilter(ContextCompat.getColor(ResultActivity.this, R.color.blue), PorterDuff.Mode.SRC_IN);
                        ivResult.setRotation(ivResult.getRotation() + 90);
                        if (ivResult.getRotation() == 360) {
                            ivResult.setRotation(0);

                        }
                        if (ivResult.getRotation() == 90 || ivResult.getRotation() == -90 || ivResult.getRotation() == 270 || ivResult.getRotation() == -270) {
                            if (scaledRatio == 0.0f) {
                                scaledRatio = Float.parseFloat(Integer.toString(ivResult.getWidth()))
                                        / Float.parseFloat(Integer.toString(ivResult.getHeight()));
                            }
                            if (isRotated == true) {
                                ivResult.setScaleX(2 - scaledRatio);
                                ivResult.setScaleY(2 - scaledRatio);
                            } else {
                                ivResult.setScaleX(scaledRatio);
                                ivResult.setScaleY(scaledRatio);
                            }
                        } else {
                            ivResult.setScaleX(1.0f);
                            ivResult.setScaleY(1.0f);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        ivRotate.setColorFilter(Color.argb(255, 255, 255, 255));
                        break;
                }
                return true;
            }
        });


        ivSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog();
            }
        });

        // Light Settings Options
        if (newBitMap != null) {
            seekBarContrast.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    Mat dst = doContrast(progress);
                    Utils.matToBitmap(dst, newBitMap);
                    ivResult.setImageBitmap(newBitMap);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            seekBarBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar arg0, int progress, boolean arg2) {
                    Mat dst = doBrightness(progress);
                    Utils.matToBitmap(dst, newBitMap);
                    ivResult.setImageBitmap(newBitMap);
                }

                @Override
                public void onStopTrackingTouch(SeekBar arg0) {
                }

                @Override
                public void onStartTrackingTouch(SeekBar arg0) {
                }
            });

        }

        // Reset Light Settings
        tvReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    seekBarBrightness.setProgress(50, true);
                    seekBarContrast.setProgress(50, true);
                } else {
                    seekBarBrightness.setProgress(50);
                    seekBarContrast.setProgress(50);
                }
                ivResult.setImageBitmap(newBitMap);
            }
        });

    }

    private Mat doContrast(int progress) {
        Mat dst = new Mat();
        tvContrast.setText("" + progress);
        dContrast = progress / 50.0;
        newMat.convertTo(dst, -1, dContrast, iBrightness);
        return dst;
    }

    private Mat doBrightness(int progress) {
        Mat dst = new Mat();
        tvBrightness.setText("" + progress);
        iBrightness = progress - 50;
        newMat.convertTo(dst, -1, dContrast, iBrightness);
        return dst;
    }

    //Alert to Save Image
    private void alertDialog() {
        AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
        builder1.setMessage("Are you sure you want to save this image?");
        builder1.setCancelable(true);

        builder1.setPositiveButton(
                "Save",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        File file = new File(imagePath);
                        boolean deleted = file.delete();
                        if (newBitMap != null) {
                            Bitmap tempBitmap = ((BitmapDrawable) ivResult.getDrawable()).getBitmap();
                            Matrix matrix = new Matrix();
                            matrix.postRotate(ivResult.getRotation());
                            Bitmap rotatedBmp = Bitmap.createBitmap(tempBitmap, 0, 0, tempBitmap.getWidth(), tempBitmap.getHeight(), matrix, true);
                            insertImage(getContentResolver(), rotatedBmp, UUID.randomUUID().toString(), "Saved Photo");
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            rotatedBmp.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                            byte[] bytes = stream.toByteArray();
                            long yourmilliseconds = System.currentTimeMillis();
                            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss");
                            Date date = new Date(yourmilliseconds);
                            File mFile = new File(getExternalFilesDir("Photobook"), sdf.format(date) + ".jpg");
                            try {
                                mFile.createNewFile();
                                FileOutputStream fileOutputStream = new FileOutputStream(mFile);
                                fileOutputStream.write(bytes);
                                fileOutputStream.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            Toast toast = Toast.makeText(ResultActivity.this, "Saved!", Toast.LENGTH_SHORT);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            dialog.cancel();
                            Intent intent = new Intent(ResultActivity.this, MainActivity.class);
                            intent.putExtra("folderPath", getExternalFilesDir("Photobook"));
                            startActivity(intent);
                        } else {
                            showToast("Error Saving Image to Gallery!");
                        }
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


    //Insert Image to Gallery
    public static final String insertImage(ContentResolver cr, Bitmap source, String title, String description) {

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, title);
        values.put(MediaStore.Images.Media.DISPLAY_NAME, title);
        values.put(MediaStore.Images.Media.DESCRIPTION, description);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        // Add the date meta data to ensure the image is added at the front of the gallery
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

        Uri url = null;
        String stringUrl = null;    /* value to be returned */

        try {
            url = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (source != null) {
                OutputStream imageOut = cr.openOutputStream(url);
                try {
                    source.compress(Bitmap.CompressFormat.JPEG, 100, imageOut);
                } finally {
                    imageOut.close();
                }

                long id = ContentUris.parseId(url);
                // Wait until MINI_KIND thumbnail is generated.
                Bitmap miniThumb = MediaStore.Images.Thumbnails.getThumbnail(cr, id, MediaStore.Images.Thumbnails.MINI_KIND, null);
                // This is for backward compatibility.
                storeThumbnail(cr, miniThumb, id, 50F, 50F, MediaStore.Images.Thumbnails.MICRO_KIND);
            } else {
                cr.delete(url, null, null);
                url = null;
            }
        } catch (Exception e) {
            if (url != null) {
                cr.delete(url, null, null);
                url = null;
            }
        }

        if (url != null) {
            stringUrl = url.toString();
        }

        return stringUrl;
    }

    private static final Bitmap storeThumbnail(ContentResolver cr, Bitmap source, long id, float width, float height, int kind) {

        // create the matrix to scale it
        Matrix matrix = new Matrix();

        float scaleX = width / source.getWidth();
        float scaleY = height / source.getHeight();

        matrix.setScale(scaleX, scaleY);

        Bitmap thumb = Bitmap.createBitmap(source, 0, 0,
                source.getWidth(),
                source.getHeight(), matrix,
                true
        );

        ContentValues values = new ContentValues(4);
        values.put(MediaStore.Images.Thumbnails.KIND, kind);
        values.put(MediaStore.Images.Thumbnails.IMAGE_ID, (int) id);
        values.put(MediaStore.Images.Thumbnails.HEIGHT, thumb.getHeight());
        values.put(MediaStore.Images.Thumbnails.WIDTH, thumb.getWidth());

        Uri url = cr.insert(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, values);

        try {
            OutputStream thumbOut = cr.openOutputStream(url);
            thumb.compress(Bitmap.CompressFormat.PNG, 100, thumbOut);
            thumbOut.close();
            return thumb;
        } catch (FileNotFoundException ex) {
            return null;
        } catch (IOException ex) {
            return null;
        }
    }

    //Image Color Options Menu
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.original:
                Utils.matToBitmap(newMat, newBitMap);
                ivResult.setImageBitmap(newBitMap);
                break;
            case R.id.greyscale:
                Mat greyscale = new Mat(bitmap.getWidth(), bitmap.getHeight(), CV_8UC1);
                cvtColor(newMat, greyscale, Imgproc.COLOR_RGB2GRAY, 1);
                Utils.matToBitmap(greyscale, newBitMap);
                ivResult.setImageBitmap(newBitMap);
                break;
            case R.id.blackwhite:
                Mat doc = new Mat(newBitMap.getWidth(), newBitMap.getHeight(), CV_8UC4);
                Utils.bitmapToMat(newBitMap, doc);
                enhanceDocument(doc);
                Utils.matToBitmap(doc, newBitMap);
                ivResult.setImageBitmap(newBitMap);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // Set Image with Bitmap Option Settings
    private void setPic(String photoPath) {

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;


        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = 1;
        bmOptions.inPurgeable = true;

        bitmap = BitmapFactory.decodeFile(photoPath, bmOptions);
        mat = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC4);
        Utils.bitmapToMat(bitmap, mat);
//        Mat blurredMat = new Mat();
//        Imgproc.GaussianBlur(mat, blurredMat, new Size(5, 5), 0);
//        Core.addWeighted(mat, 1.5, blurredMat, 0.5, 0.0, mat);

//        Mat dst = BrightnessAndContrastAuto(mat, 1);
        Mat mat2 = mat.clone();
        gammaValue = autoGammaValue(mat2);
        doGammaCorrection(mat);
        doWhiteBalance(mat,10);
        Utils.matToBitmap(mat, bitmap);
        ivResult.setImageBitmap(bitmap);
//        Picasso.get()
//                .load(R.drawable.gradient_bg)
//                .into(ivResult);
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

    private void colorThresh(Mat src, int threshold) {
        Size srcSize = src.size();
        int size = (int) (srcSize.height * srcSize.width) * 3;
        byte[] d = new byte[size];
        src.get(0, 0, d);

        for (int i = 0; i < size; i += 3) {

            // the "& 0xff" operations are needed to convert the signed byte to double

            // avoid unneeded work
            if ((double) (d[i] & 0xff) == 255) {
                continue;
            }

            double max = Math.max(Math.max((double) (d[i] & 0xff), (double) (d[i + 1] & 0xff)),
                    (double) (d[i + 2] & 0xff));
            double mean = ((double) (d[i] & 0xff) + (double) (d[i + 1] & 0xff)
                    + (double) (d[i + 2] & 0xff)) / 3;

            if (max > threshold && mean < max * 0.8) {
                d[i] = (byte) ((double) (d[i] & 0xff) * 255 / max);
                d[i + 1] = (byte) ((double) (d[i + 1] & 0xff) * 255 / max);
                d[i + 2] = (byte) ((double) (d[i + 2] & 0xff) * 255 / max);
            } else {
                d[i] = d[i + 1] = d[i + 2] = 0;
            }
        }
        src.put(0, 0, d);
    }

    private Mat enhanceDocument(Mat src) {
        if (colorMode && filterMode) {
            src.convertTo(src, -1, colorGain, colorBias);
            Mat mask = new Mat(src.size(), CV_8UC1);
            cvtColor(src, mask, COLOR_RGBA2GRAY);

            Mat copy = new Mat(src.size(), CV_8UC3);
            src.copyTo(copy);

            Imgproc.adaptiveThreshold(mask, mask, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 15, 15);

            src.setTo(new Scalar(255, 255, 255));
            copy.copyTo(src, mask);

            copy.release();
            mask.release();

            // special color threshold algorithm
            colorThresh(src, colorThresh);
        } else if (!colorMode) {
            cvtColor(src, src, COLOR_RGBA2GRAY);
            if (filterMode) {
                Imgproc.adaptiveThreshold(src, src, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 15, 15);
            }
        }
        return src;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 0: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Toast.makeText(ResultActivity.this, "Permission not granted", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.color_menu, menu);
        return true;
    }

    //    private void doWhiteBalance(Mat src, float perc){
//        if (src.empty()) {
//            System.err.println("Cannot read image: " + imagePath);
//            System.exit(0);
//        }
//        List<Mat> bgrPlanes = new ArrayList<>();
//        Core.split(src, bgrPlanes);
//        int histSize = 256;
//        float[] range = {0, 256}; //the upper boundary is exclusive
//        MatOfFloat histRange = new MatOfFloat(range);
//        boolean accumulate = false;
//        Mat bHist = new Mat(), gHist = new Mat(), rHist = new Mat();
//        Imgproc.calcHist(bgrPlanes, new MatOfInt(0), new Mat(), bHist, new MatOfInt(histSize), histRange, accumulate);
//        Imgproc.calcHist(bgrPlanes, new MatOfInt(1), new Mat(), gHist, new MatOfInt(histSize), histRange, accumulate);
//        Imgproc.calcHist(bgrPlanes, new MatOfInt(2), new Mat(), rHist, new MatOfInt(histSize), histRange, accumulate);
////        int histW = 512, histH = 400;
////        int binW = (int) Math.round((double) histW / histSize);
//        Mat histImage = new Mat( src.rows(), src.cols(), CvType.CV_8UC3, new Scalar( 0,0,0) );
//        Core.normalize(bHist, bHist, 0, histImage.rows(), Core.NORM_MINMAX);
//        Core.normalize(gHist, gHist, 0, histImage.rows(), Core.NORM_MINMAX);
//        Core.normalize(rHist, rHist, 0, histImage.rows(), Core.NORM_MINMAX);
//        float[] bHistData = new float[(int) (bHist.total() * bHist.channels())];
//        bHist.get(0, 0, bHistData);
//        float[] gHistData = new float[(int) (gHist.total() * gHist.channels())];
//        gHist.get(0, 0, gHistData);
//        float[] rHistData = new float[(int) (rHist.total() * rHist.channels())];
//        rHist.get(0, 0, rHistData);
//
//        // Get the min and max pixel values indicating the 5% of pixels at the ends of the histograms
//        float r_min_val = -1, g_min_val = -1, b_min_val = -1; // Dummy value
//        float r_max_val = -1, g_max_val = -1, b_max_val = -1; // Dummy value
//
//        for (int i=0; i < 256; i++){
//            if (rHistData[i] >= perc && r_min_val == -1){
//                r_min_val = (float) i;
//            }
//            if (rHistData[i] >= (1.0 - perc) && r_max_val == -1){
////                cout << rHistData[i] << end1;
//                r_max_val = (float) i;
//            }
//            if (gHistData[i] >= perc && g_min_val == -1){
//                g_min_val = (float) i;
//            }
//            if (gHistData[i] >= (1.0 - perc) && g_max_val == -1){
//                g_max_val = (float) i;
//            }
//            if (bHistData[i] >= perc && b_min_val == -1){
//                b_min_val = (float) i;
//            }
//            if (bHistData[i] >= (1.0 - perc) && b_max_val == -1){
//                b_max_val = (float) i;
//            }
//        }
//        byte[] r_lut = new byte[256];
//        byte[] g_lut = new byte[256];
//        byte[] b_lut = new byte[256];
//        for( int i = 1; i < histSize; i++ ) {
////            Imgproc.line(histImage, new Point(binW * (i - 1), histH - Math.round(bHistData[i - 1])),
////                    new Point(binW * (i), histH - Math.round(bHistData[i])), new Scalar(255, 0, 0), 2);
////            Imgproc.line(histImage, new Point(binW * (i - 1), histH - Math.round(gHistData[i - 1])),
////                    new Point(binW * (i), histH - Math.round(gHistData[i])), new Scalar(0, 255, 0), 2);
////            Imgproc.line(histImage, new Point(binW * (i - 1), histH - Math.round(rHistData[i - 1])),
////                    new Point(binW * (i), histH - Math.round(rHistData[i])), new Scalar(0, 0, 255), 2);
//            b_lut[i] = saturate(((i - b_min_val) / (b_max_val - b_min_val)) * 255.0);
//            g_lut[i] = saturate(((i - g_min_val) / (g_max_val - g_min_val)) * 255.0);
//            r_lut[i] = saturate(((i - r_min_val) / (r_max_val - r_min_val)) * 255.0);
////            lookUpTable.put(0, 0, lookUpTableData);
////            Core.LUT(src, lookUpTable, src);
//        }
//        Mat out = src.clone();
//        ArrayList<Mat> rgbList = new ArrayList<>();
//        rgbList.add(r_lut,g_lut,b_lut);
//        Core.merge();
//        out.put(0, 0,r_lut,g_lut,b_lut);
//        Core.LUT(src, lookUpTable, src);
//
//        Bitmap histBmp = Bitmap.createBitmap(512, 400, Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(histImage, histBmp );
//        ivResult.setImageBitmap(histBmp);
//    }
    public static Mat doWhiteBalance(Mat img, int percent) {
        if (percent <= 0)
            percent = 5;
        Imgproc.cvtColor(img,img,COLOR_RGBA2RGB);
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
            results.add(channel);
        }
        Mat outval = new Mat();
        Core.merge(results, outval);

        return outval;
    }

}
