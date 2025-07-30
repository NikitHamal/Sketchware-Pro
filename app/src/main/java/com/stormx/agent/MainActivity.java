package com.stormx.agent;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import pro.sketchware.activities.main.fragments.ai.AiFragment;
import com.stormx.agent.R; // ensure resource import

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new AiFragment())
                .commit();
        }
    }
}