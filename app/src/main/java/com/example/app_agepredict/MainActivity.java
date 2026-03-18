package com.example.app_agepredict;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private EditText editTextAge;
    private Button buttonContinue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editTextAge = findViewById(R.id.editTextAge);
        buttonContinue = findViewById(R.id.buttonContinue);

        buttonContinue.setOnClickListener(v -> {
            String ageText = editTextAge.getText().toString();
            if (!ageText.isEmpty()) {
                int age = Integer.parseInt(ageText);
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra("actualAge", age);
                startActivity(intent);
            }
        });
    }
}
