package com.example.tensorflow;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {

    Handler handler;

    private static final String KEY = "*******************";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler();

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            try {
                if (type.startsWith("image")) {
                    Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    predictPic(uri);
                } else {
                    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                    predictUrl(text);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void predictPic(Uri uri) throws IOException {
        if (uri == null) {
            return;
        }
        ImageView imageView = findViewById(R.id.image);
        imageView.setImageURI(uri);
        Log.d("sdsdsd", "url: " + uri.toString());

        MappedByteBuffer model = FileUtil.loadMappedFile(this, "model.tflite");
        Interpreter.Options options = new Interpreter.Options();
        Interpreter interpreter = new Interpreter(model, options);
        int[] inputShape = interpreter.getInputTensor(0).shape();
        DataType inputDataType = interpreter.getInputTensor(0).dataType();
        int[] outputShape = interpreter.getOutputTensor(0).shape();
        DataType outputDataType = interpreter.getOutputTensor(0).dataType();

        Log.d("sdsdsd", "input shape: " + Arrays.toString(inputShape));
        Log.d("sdsdsd", "input data type: " + inputDataType.name());
        Log.d("sdsdsd", "output shape: " + Arrays.toString(outputShape));
        Log.d("sdsdsd", "output data type: " + outputDataType.name());

        TensorImage inputImage = new TensorImage(inputDataType);
        Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
        inputImage.load(bitmap);

        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeOp(inputShape[1], inputShape[2], ResizeOp.ResizeMethod.BILINEAR))
                        .build();

        inputImage = imageProcessor.process(inputImage);

        TensorBuffer outputBuffer =
                TensorBuffer.createFixedSize(outputShape, outputDataType);

        interpreter.run(inputImage.getBuffer(), outputBuffer.getBuffer());

        List<String> labels = FileUtil.loadLabels(this, "labels.txt");
        Log.d("sdsdsd", "labels: " + Arrays.toString(labels.toArray()));

        float[] resultList = outputBuffer.getFloatArray();
        Log.d("sdsdsd", "result: " + Arrays.toString(resultList));

        int index = 0;
        float result = 0;
        for (int i = 0; i < resultList.length; i++) {
            if (resultList[i] > result) {
                result = resultList[i];
                index = i;
            }
        }
        TextView textView = findViewById(R.id.result);
        textView.setText(labels.get(index));

//        TensorProcessor probabilityProcessor =
//                new TensorProcessor.Builder().build();
//
//        if (null != labels) {
//            // Map of labels and their corresponding probability
//            TensorLabel tensorLabels = new TensorLabel(labels,
//                    probabilityProcessor.process(outputBuffer));
//
//            // Create a map to access the result based on label
//            Map<String, Float> floatMap = tensorLabels.getMapWithFloatValue();
//
//            for (String label : labels) {
//                float result = floatMap.get(label);
//                Log.d("sdsdsd", "label: " + label + "\t" + result);
//            }
//        }
    }

    private void predictUrl(String text) {
        Log.d("sdsdsd", "url: " + text);

        loadImage(text);
        predict(text);
    }

    private void loadImage(final String urlString) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    InputStream inputStream = connection.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            ((ImageView)findViewById(R.id.image)).setImageBitmap(bitmap);
                        }
                    });
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void predict(final String urlString) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://westus2.api.cognitive.microsoft.com/customvision/v3.0/Prediction/f8f373a4-86aa-4d33-b705-f9e59ea8ed31/classify/iterations/beartest/url");

                    HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Prediction-Key", KEY);
                    connection.addRequestProperty("Content-Type", "application/json");

                    String body = "{\"Url\": \"" + urlString + "\"}";
                    String bodyLength = "" + body.length();
                    connection.addRequestProperty("Content-Length", bodyLength);
                    connection.setDoInput(true);
                    connection.setDoOutput(true);
                    DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
                    dataOutputStream.write(body.getBytes());
                    dataOutputStream.flush();
                    dataOutputStream.close();
                    if (connection.getResponseCode() == 200) {
                        InputStream inputStream = connection.getInputStream();
                        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line);
                            response.append("\r");
                        }
                        br.close();
                        Log.d("sdsdsd", response.toString());
                        JSONObject jsonObject = new JSONObject(response.toString());
                        JSONArray resultArray = jsonObject.getJSONArray("predictions");
                        double result = 0;
                        String tagName = "";
                        for (int i = 0; i < resultArray.length(); i++) {
                            JSONObject object = resultArray.getJSONObject(i);
                            double probability = object.getDouble("probability");
                            if (probability > result) {
                                result = probability;
                                tagName = object.getString("tagName");
                            }
                        }
                        final String resultName = tagName;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                ((TextView)findViewById(R.id.result)).setText(resultName);
                            }
                        });
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        }).start();
    }
}
