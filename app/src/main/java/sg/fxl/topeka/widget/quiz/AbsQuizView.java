/*
 * Copyright 2015 Google Inc.
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

package sg.fxl.topeka.widget.quiz;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.ColorInt;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MarginLayoutParamsCompat;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.util.Property;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import sg.fxl.topeka.helper.ApiLevelHelper;
import sg.fxl.topeka.helper.ViewUtils;
import sg.fxl.topeka.model.Quiz;
import sg.fxl.topeka.model.quiz.QuizQuestion;
import sg.fxl.topeka.widget.fab.CheckableFab;
import sg.fxl.topekaport.QuizActivity;
import sg.fxl.topekaport.QuizSetting;
import sg.fxl.topekaport.R;

/**
 * This is the base class for displaying a {@link QuizQuestion}.
 * <p>
 * Subclasses need to implement {@link AbsQuizView#createQuizContentView()}
 * in order to allow solution of a quiz.
 * </p>
 * <p>
 * Also {@link AbsQuizView#allowAnswer(boolean)} needs to be called with
 * <code>true</code> in order to mark the quiz solved.
 * </p>
 *
 * @param <Q> The type of {@link QuizQuestion} you want to
 *            display.
 */
public abstract class AbsQuizView<Q extends QuizQuestion> extends FrameLayout {

    private static final int ANSWER_HIDE_DELAY = 500;
    private static final int FOREGROUND_COLOR_CHANGE_DELAY = 750;
    private final int spacingDouble;
    private final LayoutInflater layoutInflater;
    private final Quiz quiz;
    private final Q quizQuestion;
    private final Interpolator linearOutSlowInInterpolator;
    private final Handler handler;
    private final InputMethodManager inputMethodManager;
    private boolean answered;
    private TextView questionView;
    private CheckableFab submitAnswer;
    private Runnable hideFabRunnable;
    private Runnable moveOffScreenRunnable;
    private QuizSetting quizSetting;

    /**
     * Enables creation of views for quizzes.
     *
     * @param context      The context for this view.
     * @param quiz         The {@link Quiz} this view is running in.
     * @param quizQuestion The actual {@link QuizQuestion} that is going to be displayed.
     */
    public AbsQuizView(Context context, Quiz quiz, Q quizQuestion) {
        super(context);
        this.quizQuestion = quizQuestion;
        this.quiz = quiz;
        spacingDouble = getResources().getDimensionPixelSize(R.dimen.spacing_double);
        layoutInflater = LayoutInflater.from(context);
        submitAnswer = getSubmitButton();
        linearOutSlowInInterpolator = new LinearOutSlowInInterpolator();
        handler = new Handler();
        inputMethodManager = (InputMethodManager) context.getSystemService
                (Context.INPUT_METHOD_SERVICE);

        setId(quizQuestion.getId());
        setUpQuestionView();
        LinearLayout container = createContainerLayout(context);
        View quizContentView = getInitializedContentView();
        addContentView(container, quizContentView);
        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft,
                                       int oldTop, int oldRight, int oldBottom) {
                removeOnLayoutChangeListener(this);
                addFloatingActionButton();
            }
        });
    }

    /**
     * Sets the behaviour for all question views.
     */
    private void setUpQuestionView() {
        questionView = (TextView) layoutInflater.inflate(R.layout.question, this, false);
        questionView.setBackgroundColor(ContextCompat.getColor(getContext(),
                quiz.getTheme().getPrimaryColor()));
        questionView.setText(getQuiz().getQuestion());
    }

    private LinearLayout createContainerLayout(Context context) {
        LinearLayout container = new LinearLayout(context);
        container.setId(R.id.absQuizViewContainer);
        container.setOrientation(LinearLayout.VERTICAL);
        return container;
    }

    private View getInitializedContentView() {
        View quizContentView = createQuizContentView();
        quizContentView.setId(R.id.quiz_content);
        quizContentView.setSaveEnabled(true);
        setDefaultPadding(quizContentView);
        if (quizContentView instanceof ViewGroup) {
            ((ViewGroup) quizContentView).setClipToPadding(false);
        }
        setMinHeightInternal(quizContentView);
        return quizContentView;
    }

    private void addContentView(LinearLayout container, View quizContentView) {
        LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        container.addView(questionView, layoutParams);
        container.addView(quizContentView, layoutParams);
        addView(container, layoutParams);
    }

    private void addFloatingActionButton() {
        final int fabSize = getResources().getDimensionPixelSize(R.dimen.size_fab);
        int bottomOfQuestionView = findViewById(R.id.question_view).getBottom();
        final LayoutParams fabLayoutParams = new LayoutParams(fabSize, fabSize,
                Gravity.END | Gravity.TOP);
        final int halfAFab = fabSize / 2;
        fabLayoutParams.setMargins(0, // left
                bottomOfQuestionView - halfAFab, //top
                0, // right
                spacingDouble); // bottom
        MarginLayoutParamsCompat.setMarginEnd(fabLayoutParams, spacingDouble);
        if (ApiLevelHelper.isLowerThan(Build.VERSION_CODES.LOLLIPOP)) {
            // Account for the fab's emulated shadow.
            fabLayoutParams.topMargin -= (submitAnswer.getPaddingTop() / 2);
        }
        addView(submitAnswer, fabLayoutParams);
    }

    private CheckableFab getSubmitButton() {
        if (null == submitAnswer) {
            submitAnswer = (CheckableFab) getLayoutInflater()
                    .inflate(R.layout.answer_submit, this, false);
            submitAnswer.hide();
            submitAnswer.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    submitAnswer(v);
                    if (inputMethodManager.isAcceptingText()) {
                        inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                    submitAnswer.setEnabled(false);
                }
            });
        }
        return submitAnswer;
    }

    public void setQuizSetting(QuizSetting quizSetting) {
        this.quizSetting = quizSetting;
    }

    private void setDefaultPadding(View view) {
        view.setPadding(spacingDouble, spacingDouble, spacingDouble, spacingDouble);
    }

    protected LayoutInflater getLayoutInflater() {
        return layoutInflater;
    }

    /**
     * Implementations should create the content view for the type of
     * {@link QuizQuestion} they want to display.
     *
     * @return the created view to solve the quiz.
     */
    protected abstract View createQuizContentView();

    /**
     * Implementations must make sure that the answer provided is evaluated and correctly rated.
     *
     * @return <code>true</code> if the question has been correctly answered, else
     * <code>false</code>.
     */
    protected abstract boolean isAnswerCorrect();

    /**
     * Save the user input to a bundle for orientation changes.
     *
     * @return The bundle containing the user's input.
     */
    public abstract Bundle getUserInput();

    /**
     * Restore the user's input.
     *
     * @param savedInput The input that the user made in a prior instance of this view.
     */
    public abstract void setUserInput(Bundle savedInput);

    public Q getQuiz() {
        return quizQuestion;
    }

    protected boolean isAnswered() {
        return answered;
    }

    /**
     * Sets the quiz to answered or unanswered.
     *
     * @param answered <code>true</code> if an answer was selected, else <code>false</code>.
     */
    protected void allowAnswer(final boolean answered) {
        if (null != submitAnswer) {
            if (answered) {
                submitAnswer.show();
            } else {
                submitAnswer.hide();
            }
            this.answered = answered;
        }
    }

    /**
     * Sets the quiz to answered if it not already has been answered.
     * Otherwise does nothing.
     */
    protected void allowAnswer() {
        if (!isAnswered()) {
            allowAnswer(true);
        }
    }

    /**
     * Allows children to submit an answer via code.
     */
    protected void submitAnswer() {
        submitAnswer(findViewById(R.id.submitAnswer));
    }

    @SuppressWarnings("UnusedParameters")
    private void submitAnswer(final View v) {
        final boolean answerCorrect = isAnswerCorrect();
        quizQuestion.setSolved(true);
        performScoreAnimation(answerCorrect);
    }

    /**
     * Animates the view nicely when the answer has been submitted.
     *
     * @param answerCorrect <code>true</code> if the answer was correct, else <code>false</code>.
     */
    private void performScoreAnimation(final boolean answerCorrect) {
        // Decide which background color to use.
        final int backgroundColor;
        if (!quizSetting.showTrueAnimationOnly) {
            backgroundColor = ContextCompat.getColor(getContext(),
                    answerCorrect ? R.color.green : R.color.red);
        } else {
            backgroundColor = ContextCompat.getColor(getContext(), R.color.green);
        }

        adjustFab(answerCorrect, backgroundColor);
        resizeView();
        moveViewOffScreen(answerCorrect);
        // Animate the foreground color to match the background color.
        // This overlays all content within the current view.
        animateForegroundColor(backgroundColor);
    }

    @SuppressLint("NewApi")
    private void adjustFab(boolean answerCorrect, int backgroundColor) {
        submitAnswer.setChecked(answerCorrect);
        submitAnswer.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        hideFabRunnable = new Runnable() {
            @Override
            public void run() {
                submitAnswer.hide();
            }
        };
        handler.postDelayed(hideFabRunnable, ANSWER_HIDE_DELAY);
    }

    private void resizeView() {
        final float widthHeightRatio = (float) getHeight() / (float) getWidth();
        // Animate X and Y scaling separately to allow different start delays.
        // object animators for x and y with different durations and then run them independently
        resizeViewProperty(View.SCALE_X, .5f, 200);
        resizeViewProperty(View.SCALE_Y, .5f / widthHeightRatio, 300);
    }

    private void resizeViewProperty(Property<View, Float> property,
                                    float targetScale, int durationOffset) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(this, property,
                1f, targetScale);
        animator.setInterpolator(linearOutSlowInInterpolator);
        animator.setStartDelay(FOREGROUND_COLOR_CHANGE_DELAY + durationOffset);
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (hideFabRunnable != null) {
            handler.removeCallbacks(hideFabRunnable);
        }
        if (moveOffScreenRunnable != null) {
            handler.removeCallbacks(moveOffScreenRunnable);
        }
        super.onDetachedFromWindow();
    }

    private void animateForegroundColor(@ColorInt final int targetColor) {
        ObjectAnimator animator = ObjectAnimator.ofInt(this, ViewUtils.FOREGROUND_COLOR,
                Color.TRANSPARENT, targetColor);
        animator.setEvaluator(new ArgbEvaluator());
        animator.setStartDelay(FOREGROUND_COLOR_CHANGE_DELAY);
        animator.start();
    }

    private void moveViewOffScreen(final boolean answerCorrect) {
        // Move the current view off the screen.
        moveOffScreenRunnable = new Runnable() {
            @Override
            public void run() {
                quiz.setScore(getQuiz(), answerCorrect);
                if (getContext() instanceof QuizActivity) {
                    ((QuizActivity) getContext()).proceed();
                }
            }
        };
        handler.postDelayed(moveOffScreenRunnable,
                FOREGROUND_COLOR_CHANGE_DELAY * 2);
    }

    private void setMinHeightInternal(View view) {
        view.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.min_height_question));
    }
}
