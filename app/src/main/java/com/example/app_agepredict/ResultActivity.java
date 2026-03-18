package com.example.app_agepredict;

import static androidx.camera.core.internal.utils.ImageUtil.rotateBitmap;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.Interpreter;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class ResultActivity extends AppCompatActivity {
    private ImageView imageViewCaptured;
    private TextView textViewPrediction;
    private Button buttonBack;
    private Interpreter tflite;
    private int actualAge;

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        // Initialize views
        imageViewCaptured = findViewById(R.id.imageViewCaptured);
        textViewPrediction = findViewById(R.id.textViewPrediction);
        buttonBack = findViewById(R.id.buttonBack);

        // Get Bitmap from Intent
        byte[] byteArray = getIntent().getByteArrayExtra("capturedImage");
        actualAge = getIntent().getIntExtra("actualAge", 0);

        Bitmap capturedImage = null;
        if (byteArray != null) {
            capturedImage = BitmapFactory.decodeStream(new ByteArrayInputStream(byteArray));
            // Rotate image 90 degrees
            capturedImage = rotateBitmap(capturedImage, 90);
        }

        // Display captured image and prediction if image exists
        if (capturedImage != null) {
            imageViewCaptured.setImageBitmap(capturedImage);
            loadModel();
            predictAndDisplayAge(capturedImage);

            // Display face regions
            detectFaceAndSplitRegions(capturedImage);
        } else {
            textViewPrediction.setText("Error: Unable to load image.");
        }

        // Handle back button click
        buttonBack.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }

    // Load TensorFlow Lite model
    private void loadModel() {
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            e.printStackTrace();
            textViewPrediction.setText("Error: Unable to load model.");
        }
    }

    // Open model file and map it to memory
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("model.tflite");
        FileInputStream fileInputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
    }

    // Predict and display age
    private void predictAndDisplayAge(Bitmap bitmap) {
        float[] input = preprocessBitmap(bitmap);
        float predictedAge = predictAge(input);
        String advice = generateAdvice(predictedAge, actualAge);
        textViewPrediction.setText("Predicted Age: " + predictedAge + "\n" + advice);
    }

    private float[] preprocessBitmap(Bitmap bitmap) {
        int inputSize = 128;
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true);

        float[] input = new float[1 * inputSize * inputSize * 3];
        int[] pixels = new int[inputSize * inputSize];
        resizedBitmap.getPixels(pixels, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            input[i * 3] = ((pixel >> 16) & 0xFF) / 255.0f;
            input[i * 3 + 1] = ((pixel >> 8) & 0xFF) / 255.0f;
            input[i * 3 + 2] = (pixel & 0xFF) / 255.0f;
        }

        return input;
    }

    private float predictAge(float[] input) {
        float[][][][] input4D = new float[1][128][128][3];
        for (int i = 0; i < input.length; i++) {
            input4D[0][i / (128 * 3)][(i / 3) % 128][i % 3] = input[i];
        }

        float[][] output = new float[1][1];
        tflite.run(input4D, output);
        return output[0][0];
    }

    private String generateAdvice(float predictedAge, int actualAge) {
        if (predictedAge < actualAge) {
            return "You look younger than your actual age!";
        } else if (predictedAge > actualAge) {
            return "You look older than your actual age. Consider maintaining a healthy lifestyle!";
        } else {
            return "You look just like your actual age!";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tflite != null) {
            tflite.close();
        }
    }

    // Detect face and split regions based on landmarks
    private void detectFaceAndSplitRegions(Bitmap capturedImage) {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);
        InputImage image = InputImage.fromBitmap(capturedImage, 0);

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (!faces.isEmpty()) {
                        Face face = faces.get(0);
                        List<Bitmap> regions = extractRegions(face, capturedImage);
                        predictAndDisplayAgeForRegions(regions);

                    } else {
                        textViewPrediction.setText("No face detected.");
                    }
                })
                .addOnFailureListener(e -> textViewPrediction.setText("Error detecting face."));
    }



    private List<Bitmap> extractRegions(Face face, Bitmap faceImg) {
        List<Bitmap> regions = new ArrayList<>();

        // Tính toán kích thước động dựa trên khoảng cách giữa hai mắt (eyeDistance)
        float eyeDistance = 0;
        PointF leftEyePos = null;
        PointF rightEyePos = null;

        // Kiểm tra xem có điểm mắt trái và mắt phải không
        if (face.getLandmark(FaceLandmark.LEFT_EYE) != null && face.getLandmark(FaceLandmark.RIGHT_EYE) != null) {
            leftEyePos = face.getLandmark(FaceLandmark.LEFT_EYE).getPosition();
            rightEyePos = face.getLandmark(FaceLandmark.RIGHT_EYE).getPosition();

            // Tính khoảng cách giữa hai mắt để xác định kích thước vùng động
            eyeDistance = Math.abs(rightEyePos.x - leftEyePos.x);
        }

        // Cắt và phóng to vùng mắt trái
        if (leftEyePos != null) {
            int leftEyeSize = (int) (eyeDistance * 0.5);
            int leftEyeX = (int) leftEyePos.x - leftEyeSize / 2;
            int leftEyeY = (int) leftEyePos.y - leftEyeSize / 2;

            Bitmap leftEyeRegion = Bitmap.createBitmap(faceImg,
                    Math.max(0, leftEyeX),
                    Math.max(0, leftEyeY),
                    Math.min(leftEyeSize, faceImg.getWidth() - leftEyeX),
                    Math.min(leftEyeSize, faceImg.getHeight() - leftEyeY));
            regions.add(Bitmap.createScaledBitmap(leftEyeRegion, 128, 128, true));
        }

        // Cắt và phóng to vùng mắt phải
        if (rightEyePos != null) {
            int rightEyeSize = (int) (eyeDistance * 0.5);
            int rightEyeX = (int) rightEyePos.x - rightEyeSize / 2;
            int rightEyeY = (int) rightEyePos.y - rightEyeSize / 2;

            Bitmap rightEyeRegion = Bitmap.createBitmap(faceImg,
                    Math.max(0, rightEyeX),
                    Math.max(0, rightEyeY),
                    Math.min(rightEyeSize, faceImg.getWidth() - rightEyeX),
                    Math.min(rightEyeSize, faceImg.getHeight() - rightEyeY));
            regions.add(Bitmap.createScaledBitmap(rightEyeRegion, 128, 128, true));
        }

        // Thêm vùng chân mày trái (ngay trên mắt trái một khoảng)
        int browSize = (int) (eyeDistance * 0.5);
        if (leftEyePos != null) {
            int leftBrowX = (int) leftEyePos.x - browSize / 2;
            int leftBrowY = (int) leftEyePos.y - browSize;

            Bitmap leftBrowRegion = Bitmap.createBitmap(faceImg,
                    Math.max(0, leftBrowX),
                    Math.max(0, leftBrowY),
                    Math.min(browSize, faceImg.getWidth() - leftBrowX),
                    Math.min(browSize, faceImg.getHeight() - leftBrowY));
            regions.add(Bitmap.createScaledBitmap(leftBrowRegion, 128, 128, true));
        }

        // Thêm vùng chân mày phải (ngay trên mắt phải một khoảng)
        if (rightEyePos != null) {
            int rightBrowX = (int) rightEyePos.x - browSize / 2;
            int rightBrowY = (int) rightEyePos.y - browSize;

            Bitmap rightBrowRegion = Bitmap.createBitmap(faceImg,
                    Math.max(0, rightBrowX),
                    Math.max(0, rightBrowY),
                    Math.min(browSize, faceImg.getWidth() - rightBrowX),
                    Math.min(browSize, faceImg.getHeight() - rightBrowY));
            regions.add(Bitmap.createScaledBitmap(rightBrowRegion, 128, 128, true));
        }

        // Cắt vùng mũi
        if (face.getLandmark(FaceLandmark.NOSE_BASE) != null) {
            PointF nosePos = face.getLandmark(FaceLandmark.NOSE_BASE).getPosition();
            int noseSize = (int) (eyeDistance * 0.8);
            int x = (int) nosePos.x - noseSize / 2;
            int y = (int) nosePos.y - noseSize / 2;

            Bitmap noseRegion = Bitmap.createBitmap(faceImg, x, y, noseSize, noseSize);
            regions.add(Bitmap.createScaledBitmap(noseRegion, 128, 128, true));
        }

        // Cắt và phóng to vùng miệng trái
        if (face.getLandmark(FaceLandmark.MOUTH_LEFT) != null) {
            PointF leftMouthPos = face.getLandmark(FaceLandmark.MOUTH_LEFT).getPosition();
            int mouthSize = (int) (eyeDistance * 0.4);
            int leftMouthX = (int) leftMouthPos.x - mouthSize / 2;
            int leftMouthY = (int) leftMouthPos.y - mouthSize / 2;

            Bitmap leftMouthRegion = Bitmap.createBitmap(faceImg,
                    Math.max(0, leftMouthX),
                    Math.max(0, leftMouthY),
                    Math.min(mouthSize, faceImg.getWidth() - leftMouthX),
                    Math.min(mouthSize, faceImg.getHeight() - leftMouthY));
            regions.add(Bitmap.createScaledBitmap(leftMouthRegion, 128, 128, true));
        }

        // Cắt và phóng to vùng miệng phải
        if (face.getLandmark(FaceLandmark.MOUTH_RIGHT) != null) {
            PointF rightMouthPos = face.getLandmark(FaceLandmark.MOUTH_RIGHT).getPosition();
            int mouthSize = (int) (eyeDistance * 0.4);
            int rightMouthX = (int) rightMouthPos.x - mouthSize / 2;
            int rightMouthY = (int) rightMouthPos.y - mouthSize / 2;

            Bitmap rightMouthRegion = Bitmap.createBitmap(faceImg,
                    Math.max(0, rightMouthX),
                    Math.max(0, rightMouthY),
                    Math.min(mouthSize, faceImg.getWidth() - rightMouthX),
                    Math.min(mouthSize, faceImg.getHeight() - rightMouthY));
            regions.add(Bitmap.createScaledBitmap(rightMouthRegion, 128, 128, true));
        }

        // Cắt và phóng to vùng má trái
        if (face.getLandmark(FaceLandmark.LEFT_CHEEK) != null) {
            PointF leftCheekPos = face.getLandmark(FaceLandmark.LEFT_CHEEK).getPosition();
            int cheekSize = (int) (eyeDistance * 0.6);
            int leftCheekX = (int) leftCheekPos.x - cheekSize / 2;
            int leftCheekY = (int) leftCheekPos.y - cheekSize / 2;

            Bitmap leftCheekRegion = Bitmap.createBitmap(faceImg,
                    Math.max(0, leftCheekX),
                    Math.max(0, leftCheekY),
                    Math.min(cheekSize, faceImg.getWidth() - leftCheekX),
                    Math.min(cheekSize, faceImg.getHeight() - leftCheekY));
            regions.add(Bitmap.createScaledBitmap(leftCheekRegion, 128, 128, true));
        }

        // Cắt và phóng to vùng má phải
        if (face.getLandmark(FaceLandmark.RIGHT_CHEEK) != null) {
            PointF rightCheekPos = face.getLandmark(FaceLandmark.RIGHT_CHEEK).getPosition();
            int cheekSize = (int) (eyeDistance * 0.6);
            int rightCheekX = (int) rightCheekPos.x - cheekSize / 2;
            int rightCheekY = (int) rightCheekPos.y - cheekSize / 2;

            Bitmap rightCheekRegion = Bitmap.createBitmap(faceImg,
                    Math.max(0, rightCheekX),
                    Math.max(0, rightCheekY),
                    Math.min(cheekSize, faceImg.getWidth() - rightCheekX),
                    Math.min(cheekSize, faceImg.getHeight() - rightCheekY));
            regions.add(Bitmap.createScaledBitmap(rightCheekRegion, 128, 128, true));
        }

        // Cắt và phóng to vùng trán (nếu có)
        if (leftEyePos != null && rightEyePos != null) {
            int foreheadSize = (int) (eyeDistance * 1.2);
            int foreheadX = (int) ((leftEyePos.x + rightEyePos.x) / 2 - foreheadSize / 2);
            int foreheadY = (int) (Math.min(leftEyePos.y, rightEyePos.y) - foreheadSize);

            Bitmap foreheadRegion = Bitmap.createBitmap(faceImg,
                    Math.max(0, foreheadX),
                    Math.max(0, foreheadY),
                    Math.min(foreheadSize, faceImg.getWidth() - foreheadX),
                    Math.min(foreheadSize, faceImg.getHeight() - foreheadY));
            regions.add(Bitmap.createScaledBitmap(foreheadRegion, 128, 128, true));
        }

        // Cắt và phóng to vùng cằm (dưới mũi một khoảng)
        if (face.getLandmark(FaceLandmark.NOSE_BASE) != null) {
            int chinSize = (int) (eyeDistance * 0.8);
            int chinX = (int) (face.getLandmark(FaceLandmark.NOSE_BASE).getPosition().x - chinSize / 2);
            int chinY = (int) (face.getLandmark(FaceLandmark.NOSE_BASE).getPosition().y + chinSize / 2);

            Bitmap chinRegion = Bitmap.createBitmap(faceImg,
                    Math.max(0, chinX),
                    Math.max(0, chinY),
                    Math.min(chinSize, faceImg.getWidth() - chinX),
                    Math.min(chinSize, faceImg.getHeight() - chinY));
            regions.add(Bitmap.createScaledBitmap(chinRegion, 128, 128, true));
        }

        return regions;
    }

    // Display face regions on the screen


    // Predict age and calculate MAE for each region
    private void displayRegionsWithMAE(List<Bitmap> regions, List<Float> maeResults) {
        LinearLayout layout = findViewById(R.id.layoutRegions);
        layout.removeAllViews(); // Xóa tất cả các view trước đó nếu có

        String[] regionNames = {"Left Eye", "Right Eye", "Left Brow", "Right Brow",
                "Nose", "Left Mouth", "Right Mouth", "Left Cheek",
                "Right Cheek", "Forehead", "Chin"};

        for (int i = 0; i < regions.size(); i++) {
            // Tạo một layout dọc để chứa ảnh và kết quả MAE cho từng vùng
            LinearLayout regionLayout = new LinearLayout(this);
            regionLayout.setOrientation(LinearLayout.VERTICAL);
            regionLayout.setPadding(8, 8, 8, 8);

            // Hiển thị ảnh của vùng
            ImageView imageView = new ImageView(this);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            imageView.setImageBitmap(regions.get(i));
            regionLayout.addView(imageView);

            // Hiển thị MAE cho vùng đó
            TextView textViewMAE = new TextView(this);
            textViewMAE.setText(regionNames[i] + ": MAE = " + maeResults.get(i));
            regionLayout.addView(textViewMAE);

            // Thêm layout của từng vùng vào layout chính
            layout.addView(regionLayout);
        }
    }
    private float calculateMAE(float predictedAge) {
        return Math.abs(predictedAge - actualAge); // Tính MAE
    }
    private void predictAndDisplayAgeForRegions(List<Bitmap> regions) {
        List<Float> maeResults = new ArrayList<>();
        for (Bitmap region : regions) {
            float[] input = preprocessBitmap(region);
            float predictedAge = predictAge(input); // Dự đoán độ tuổi cho mỗi vùng
            float mae = calculateMAE(predictedAge); // Tính MAE giữa độ tuổi dự đoán và thực tế
            maeResults.add(mae); // Thêm MAE vào danh sách kết quả
        }
        displayRegionsWithMAE(regions, maeResults); // Hiển thị kết quả MAE cho từng vùng
    }
}
