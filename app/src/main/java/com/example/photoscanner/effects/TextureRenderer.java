/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.photoscanner.effects;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLException;
import android.util.Log;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


public class TextureRenderer {
    private static final String TAG = TextureRenderer.class.getCanonicalName();
    private int mProgram;
    private int mTexSamplerHandle;
    private int mTexCoordHandle;
    private int mPosCoordHandle;

    private FloatBuffer mTexVertices;
    private FloatBuffer mPosVertices;

    private int mViewWidth;
    private int mViewHeight;

    private int mTexWidth;
    private int mTexHeight;

    private ByteBuffer byteBitmapBuffer;

    private static final int TEXTURE_WIDTH = 4096;
    private static final int TEXTURE_HEIGHT = 4096;
    private static final int TEXTURE_SIZE = TEXTURE_WIDTH * TEXTURE_HEIGHT * 4;

    private static final String VERTEX_SHADER =
        "attribute vec4 a_position;\n" +
        "attribute vec2 a_texcoord;\n" +
        "varying vec2 v_texcoord;\n" +
        "void main() {\n" +
        "  gl_Position = a_position;\n" +
        "  v_texcoord = a_texcoord;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
        "precision mediump float;\n" +
        "uniform sampler2D tex_sampler;\n" +
        "varying vec2 v_texcoord;\n" +
        "void main() {\n" +
        "  gl_FragColor = texture2D(tex_sampler, v_texcoord);\n" +
        "}\n";

    private static final float[] TEX_VERTICES = {
        0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f
    };

    private static final float[] POS_VERTICES = {
        -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f
    };

    private static final int FLOAT_SIZE_BYTES = 4;

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV failed to load");
        } else {
            Log.d(TAG, "OpenCV successfully loaded");
        }
    }

    public void init() {
        Log.i(TAG, "init::");

        // Create program
        mProgram = GLToolbox.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);

        // Bind attributes and uniforms
        mTexSamplerHandle = GLES20.glGetUniformLocation(mProgram,
                "tex_sampler");
        mTexCoordHandle = GLES20.glGetAttribLocation(mProgram, "a_texcoord");
        mPosCoordHandle = GLES20.glGetAttribLocation(mProgram, "a_position");

        // Setup coordinate buffers
        mTexVertices = ByteBuffer.allocateDirect(
                TEX_VERTICES.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTexVertices.put(TEX_VERTICES).position(0);
        mPosVertices = ByteBuffer.allocateDirect(
                POS_VERTICES.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mPosVertices.put(POS_VERTICES).position(0);

        Log.i(TAG, "init:: allocate buffers");
        try {
            if (byteBitmapBuffer == null) {
                byteBitmapBuffer = ByteBuffer.allocate(TEXTURE_SIZE);
            }
        }catch(Exception e){
            Log.w(TAG, e);
        }


    }

    public void tearDown() {
        Log.i(TAG, "tearDown:: mProgram: " + mProgram );
        if (mProgram != 0) {
            GLES20.glDeleteProgram(mProgram);
        }
    }

    public void updateTextureSize(int texWidth, int texHeight) {
        Log.i(TAG, "updateTextureSize:: size: " + texWidth + " " + texHeight);

        mTexWidth = texWidth;
        mTexHeight = texHeight;
        mViewWidth = mTexWidth;
        mViewHeight = mTexHeight;
//        computeOutputVertices();
    }

//    public void updateViewSize(int viewWidth, int viewHeight) {
//        mViewWidth = viewWidth;
//        mViewHeight = viewHeight;
//        computeOutputVertices();
//    }

    public void renderTexture(int texId) {
        Log.i(TAG, "renderTexture:: texId: " + texId );

        // Bind default FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        // Use our shader program
        GLES20.glUseProgram(mProgram);
        GLToolbox.checkGlError("glUseProgram");


        // Set viewport
        GLES20.glViewport(0, 0, mViewWidth, mViewHeight);
        GLToolbox.checkGlError("glViewport");

        // Disable blending
        GLES20.glDisable(GLES20.GL_BLEND);

        // Set the vertex attributes
        GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT, false,
                0, mTexVertices);
        GLES20.glEnableVertexAttribArray(mTexCoordHandle);
        GLES20.glVertexAttribPointer(mPosCoordHandle, 2, GLES20.GL_FLOAT, false,
                0, mPosVertices);
        GLES20.glEnableVertexAttribArray(mPosCoordHandle);
        GLToolbox.checkGlError("vertex attribute setup");

        // Set the input texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLToolbox.checkGlError("glActiveTexture");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLToolbox.checkGlError("glBindTexture");
        GLES20.glUniform1i(mTexSamplerHandle, 0);

        // Draw
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

//    private void computeOutputVertices() {
//        if (mPosVertices != null) {
//            float imgAspectRatio = mTexWidth / (float)mTexHeight;
//            float viewAspectRatio = mViewWidth / (float)mViewHeight;
//            float relativeAspectRatio = viewAspectRatio / imgAspectRatio;
//            float x0, y0, x1, y1;
//            if (relativeAspectRatio > 1.0f) {
//                x0 = -1.0f / relativeAspectRatio;
//                y0 = -1.0f;
//                x1 = 1.0f / relativeAspectRatio;
//                y1 = 1.0f;
//            } else {
//                x0 = -1.0f;
//                y0 = -relativeAspectRatio;
//                x1 = 1.0f;
//                y1 = relativeAspectRatio;
//            }
//            float[] coords = new float[] { x0, y0, x1, y0, x0, y1, x1, y1 };
//            mPosVertices.put(coords).position(0);
//        }
//    }

    public Bitmap getTextureBitmap() {

        if (byteBitmapBuffer == null){
            Log.w(TAG, "getTextureBitmap:: buffer is null");
            return null;
        }

        if (byteBitmapBuffer.capacity() < (mTexWidth * mTexHeight * 4)){
            Log.w(TAG, "getTextureBitmap:: bitmap exceeds buffer size");
            return null;
        }

        byteBitmapBuffer.clear();

        Log.d(TAG, "getTextureBitmap: size: " + mTexWidth + " " + mTexHeight);
        long startTime = System.currentTimeMillis();
        try {
            GLES20.glReadPixels(0, 0, mTexWidth, mTexHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBitmapBuffer);
        } catch (GLException e) {
            Log.w(TAG, e);
        }
        Mat src = new Mat(mTexHeight, mTexWidth, CvType.CV_8UC4);
        src.put(0, 0, byteBitmapBuffer.array(), 0, mTexHeight * mTexWidth * 4);
        Mat dst = new Mat(src.size(), src.type());
        Core.flip(src, dst, 0);
        src.release();

        Bitmap output = null;
        try {
            output = Bitmap.createBitmap(mTexWidth, mTexHeight, Bitmap.Config.ARGB_8888);
            if (output != null) {
                Utils.matToBitmap(dst, output);
            }
        }catch(Exception e){
            Log.w(TAG, e);
        }

        dst.release();

        Log.d(TAG, "getTextureBitmap: timeTaken: " + (System.currentTimeMillis() - startTime) + " ms");
        Log.i(TAG, "getTextureBitmap: output: " + output);
        return output;
    }
}
