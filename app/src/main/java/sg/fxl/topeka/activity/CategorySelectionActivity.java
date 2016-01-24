/*
 * Copyright 2015 Google Inc. All Rights Reserved.
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

package sg.fxl.topeka.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;

import sg.fxl.topeka.model.Category;
import sg.fxl.topeka.model.quiz.Quiz;

import sg.fxl.topekaport.R;
public class CategorySelectionActivity extends AppCompatActivity {
    private static final int REQUEST_CATEGORY = 0x2300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCompat.startActivityForResult(this, new Intent(this, QuizActivity.class), REQUEST_CATEGORY, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == R.id.solved) {
            Category category = data.getParcelableExtra("quiz");
            for (Quiz quiz : category.getQuizzes()) {
                System.out.println(quiz.getAnswer());
            }
        }
    }
}
