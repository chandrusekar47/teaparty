/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.samples.SampleApplication.utils;

import android.opengl.GLES20;
import android.util.Log;

import com.vuforia.Matrix44F;
import com.vuforia.Vec2F;
import com.vuforia.Vec3F;
import com.vuforia.Vec4F;
import com.vuforia.Vec4I;

import static com.vuforia.samples.SampleApplication.utils.SampleMath.Vec4FDiv;
import static com.vuforia.samples.SampleApplication.utils.SampleMath.Vec4FTransform;


public class SampleUtils
{

    private static final String LOGTAG = "SampleUtils";


    static int initShader(int shaderType, String source)
    {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0)
        {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);

            int[] glStatusVar = { GLES20.GL_FALSE };
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, glStatusVar,
                0);
            if (glStatusVar[0] == GLES20.GL_FALSE)
            {
                Log.e(LOGTAG, "Could NOT compile shader " + shaderType + " : "
                    + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }

        }

        return shader;
    }

    Vec3F projectToScreen2(Matrix44F modelViewProjection, Vec3F modelSpaceCoordinates, Vec4I viewPort, float screenScale, Vec2F screenSize)
    {
        float[] screenSizeData = screenSize.getData();
        System.out.printf("ScreenSize is: %f, %f\n", screenSizeData[0],screenSizeData[1]);
        int[] viewPortData = viewPort.getData();
        System.out.printf("Viewport is X,Y: %d,%d  SizeX,SizeY: %d, %d\n", viewPortData[0], viewPortData[1], viewPortData[2], viewPortData[3]);
        float[] modelSpaceCoordinatesData = modelSpaceCoordinates.getData();
        Vec4F homogeneousCoordinates = new Vec4F(modelSpaceCoordinatesData[0], modelSpaceCoordinatesData[1], modelSpaceCoordinatesData[2], 1.0f);

        Vec4F deviceCoordinatesCoordinates = Vec4FTransform(homogeneousCoordinates, modelViewProjection);
        float[] deviceCoordinatesCoordinatesData = deviceCoordinatesCoordinates.getData();
        System.out.printf("Device Coordinates: %f, %f, %f, %f\n", deviceCoordinatesCoordinatesData[0],deviceCoordinatesCoordinatesData[1],deviceCoordinatesCoordinatesData[2],deviceCoordinatesCoordinatesData[3]);

        Vec4F ndc = Vec4FDiv(deviceCoordinatesCoordinates, deviceCoordinatesCoordinatesData[3]);
        float[] ndcData = ndc.getData();
        System.out.printf("NDC [-1, 1]: %f, %f, %f\n", ndcData [0],ndcData [1],ndcData [2]);


        //transform to screen coordinates
        // bring to [0,1] and scale to viewPort
        Vec3F windowCoordinates = new Vec3F((ndcData[0] + 1) / 2.0f * viewPortData[2] + viewPortData[0], (ndcData[1] + 1) / 2.0f * viewPortData[3] + viewPortData[1], (ndcData[2] + 1) / 2.0f);
        float[] windowCoordinatesData = windowCoordinates.getData();
        System.out.printf("Windowcoords: %f, %f, %f\n", windowCoordinatesData[0],windowCoordinatesData[1],windowCoordinatesData[2]);

        float aspectRatio = screenSizeData[1]/viewPortData[2];    // window Height / viewPortHeight  for portrait

        Vec3F screenCoordinates = new Vec3F(windowCoordinatesData[1] * aspectRatio, (viewPortData[2] - windowCoordinatesData[0]) * aspectRatio, windowCoordinatesData[2]);
        float[] screenCoordinatesData = screenCoordinates.getData();
        System.out.printf("Screencoordinates: %f, %f, %f\n", screenCoordinatesData[0],screenCoordinatesData[1],screenCoordinatesData[2]);

        return screenCoordinates;
    }

    public static int createProgramFromShaderSrc(String vertexShaderSrc,
        String fragmentShaderSrc)
    {
        int vertShader = initShader(GLES20.GL_VERTEX_SHADER, vertexShaderSrc);
        int fragShader = initShader(GLES20.GL_FRAGMENT_SHADER,
            fragmentShaderSrc);

        if (vertShader == 0 || fragShader == 0)
            return 0;

        int program = GLES20.glCreateProgram();
        if (program != 0)
        {
            GLES20.glAttachShader(program, vertShader);
            checkGLError("glAttchShader(vert)");

            GLES20.glAttachShader(program, fragShader);
            checkGLError("glAttchShader(frag)");

            GLES20.glLinkProgram(program);
            int[] glStatusVar = { GLES20.GL_FALSE };
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, glStatusVar,
                0);
            if (glStatusVar[0] == GLES20.GL_FALSE)
            {
                Log.e(
                    LOGTAG,
                    "Could NOT link program : "
                        + GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }

        return program;
    }


    public static void checkGLError(String op)
    {
        for (int error = GLES20.glGetError(); error != 0; error = GLES20
            .glGetError())
            Log.e(
                LOGTAG,
                "After operation " + op + " got glError 0x"
                    + Integer.toHexString(error));
    }


    // Transforms a screen pixel to a pixel onto the camera image,
    // taking into account e.g. cropping of camera image to fit different aspect
    // ratio screen.
    // for the camera dimensions, the width is always bigger than the height
    // (always landscape orientation)
    // Top left of screen/camera is origin
    public static void screenCoordToCameraCoord(int screenX, int screenY,
        int screenDX, int screenDY, int screenWidth, int screenHeight,
        int cameraWidth, int cameraHeight, int[] cameraX, int[] cameraY,
        int[] cameraDX, int[] cameraDY, int displayRotation, int cameraRotation)
    {
        float videoWidth, videoHeight;
        videoWidth = (float) cameraWidth;
        videoHeight = (float) cameraHeight;

        // Compute the angle by which the camera image should be rotated clockwise so that it is
        // shown correctly on the display given its current orientation.
        int correctedRotation = ((((displayRotation*90)-cameraRotation)+360)%360)/90;

        switch (correctedRotation) {

            case 0:
                break;

            case 1:

                int tmp = screenX;
                screenX = screenHeight - screenY;
                screenY = tmp;

                tmp = screenDX;
                screenDX = screenDY;
                screenDY = tmp;

                tmp = screenWidth;
                screenWidth = screenHeight;
                screenHeight = tmp;

                break;

            case 2:
                screenX = screenWidth - screenX;
                screenY = screenHeight - screenY;
                break;

            case 3:

                tmp = screenX;
                screenX = screenY;
                screenY = screenWidth - tmp;

                tmp = screenDX;
                screenDX = screenDY;
                screenDY = tmp;

                tmp = screenWidth;
                screenWidth = screenHeight;
                screenHeight = tmp;

                break;
        }

        float videoAspectRatio = videoHeight / videoWidth;
        float screenAspectRatio = (float) screenHeight / (float) screenWidth;

        float scaledUpX;
        float scaledUpY;
        float scaledUpVideoWidth;
        float scaledUpVideoHeight;

        if (videoAspectRatio < screenAspectRatio)
        {
            // the video height will fit in the screen height
            scaledUpVideoWidth = (float) screenHeight / videoAspectRatio;
            scaledUpVideoHeight = screenHeight;
            scaledUpX = (float) screenX
                + ((scaledUpVideoWidth - (float) screenWidth) / 2.0f);
            scaledUpY = (float) screenY;
        } else
        {
            // the video width will fit in the screen width
            scaledUpVideoHeight = (float) screenWidth * videoAspectRatio;
            scaledUpVideoWidth = screenWidth;
            scaledUpY = (float) screenY
                + ((scaledUpVideoHeight - (float) screenHeight) / 2.0f);
            scaledUpX = (float) screenX;
        }

        if (cameraX != null && cameraX.length > 0)
        {
            cameraX[0] = (int) ((scaledUpX / (float) scaledUpVideoWidth) * videoWidth);
        }

        if (cameraY != null && cameraY.length > 0)
        {
            cameraY[0] = (int) ((scaledUpY / (float) scaledUpVideoHeight) * videoHeight);
        }

        if (cameraDX != null && cameraDX.length > 0)
        {
            cameraDX[0] = (int) (((float) screenDX / (float) scaledUpVideoWidth) * videoWidth);
        }

        if (cameraDY != null && cameraDY.length > 0)
        {
            cameraDY[0] = (int) (((float) screenDY / (float) scaledUpVideoHeight) * videoHeight);
        }
    }


    public static float[] getOrthoMatrix(float nLeft, float nRight,
        float nBottom, float nTop, float nNear, float nFar)
    {
        float[] nProjMatrix = new float[16];

        int i;
        for (i = 0; i < 16; i++)
            nProjMatrix[i] = 0.0f;

        nProjMatrix[0] = 2.0f / (nRight - nLeft);
        nProjMatrix[5] = 2.0f / (nTop - nBottom);
        nProjMatrix[10] = 2.0f / (nNear - nFar);
        nProjMatrix[12] = -(nRight + nLeft) / (nRight - nLeft);
        nProjMatrix[13] = -(nTop + nBottom) / (nTop - nBottom);
        nProjMatrix[14] = (nFar + nNear) / (nFar - nNear);
        nProjMatrix[15] = 1.0f;

        return nProjMatrix;
    }

}
