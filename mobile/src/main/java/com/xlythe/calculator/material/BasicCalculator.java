/*
* Copyright (C) 2014 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.xlythe.calculator.material;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;

import com.xlythe.calculator.material.CalculatorExpressionEvaluator.EvaluateCallback;
import com.xlythe.calculator.material.util.TextUtil;
import com.xlythe.calculator.material.view.CalculatorPadView;
import com.xlythe.calculator.material.view.DisplayOverlay;
import com.xlythe.calculator.material.view.EqualsImageButton;
import com.xlythe.calculator.material.view.FormattedNumberEditText;
import com.xlythe.calculator.material.view.LongClickButton;
import com.xlythe.calculator.material.view.ResizingEditText.OnTextSizeChangeListener;
import com.xlythe.math.Constants;
import com.xlythe.math.EquationFormatter;
import com.xlythe.math.History;
import com.xlythe.math.HistoryEntry;
import com.xlythe.math.Persist;
import com.xlythe.math.Solver;
import com.xlythe.view.floating.AnimationFinishedListener;

import io.codetail.animation.SupportAnimator;
import io.codetail.animation.ViewAnimationUtils;
import io.codetail.widget.RevealView;

/**
 * A very basic calculator. Maps button clicks to the display, and solves on each key press.
 */
public abstract class BasicCalculator extends Activity
        implements OnTextSizeChangeListener, EvaluateCallback, OnLongClickListener {

    /**
     * Constant for an invalid resource id.
     */
    public static final int INVALID_RES_ID = -1;
    protected static final String NAME = "Calculator";
    // instance state keys
    private static final String KEY_CURRENT_STATE = NAME + "_currentState";
    private static final String KEY_CURRENT_EXPRESSION = NAME + "_currentExpression";
    private final ViewGroup.LayoutParams mLayoutParams = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    private CalculatorState mCurrentState;
    private CalculatorExpressionTokenizer mTokenizer;
    private CalculatorExpressionEvaluator mEvaluator;
    private DisplayOverlay mDisplayView;
    private FormattedNumberEditText mFormulaEditText;
    private TextView mResultEditText;
    private CalculatorPadView mPadViewPager;
    private View mDeleteButton;
    private EqualsImageButton mEqualButton;
    private View mClearButton;
    private final TextWatcher mFormulaTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (mCurrentState != CalculatorState.GRAPHING) {
                setState(CalculatorState.INPUT);
            }
            mEvaluator.evaluate(editable, BasicCalculator.this);
        }
    };
    private View mCurrentButton;
    private Animator mCurrentAnimator;
    private History mHistory;
    private final OnKeyListener mFormulaOnKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                        mCurrentButton = mEqualButton;
                        onEquals();
                    }
                    // ignore all other actions
                    return true;
            }
            return false;
        }
    };
    private HistoryAdapter mHistoryAdapter;
    private Persist mPersist;
    private ViewGroup mDisplayForeground;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);
        savedInstanceState = savedInstanceState == null ? Bundle.EMPTY : savedInstanceState;
        initialize(savedInstanceState);
        mEvaluator.evaluate(mFormulaEditText.getCleanText(), this);
    }

    protected void initialize(Bundle savedInstanceState) {
        // Rebuild constants. If the user changed their locale, it won't kill the app
        // but it might change a decimal point from . to ,
        Constants.rebuildConstants();

        mDisplayView = (DisplayOverlay) findViewById(R.id.display);
        mDisplayView.setFade(findViewById(R.id.history_fade));
        mDisplayForeground = (ViewGroup) findViewById(R.id.the_clear_animation);
        mFormulaEditText = (FormattedNumberEditText) findViewById(R.id.formula);
        mResultEditText = (TextView) findViewById(R.id.result);
        mPadViewPager = (CalculatorPadView) findViewById(R.id.pad_pager);
        mDeleteButton = findViewById(R.id.del);
        mClearButton = findViewById(R.id.clr);
        mEqualButton = (EqualsImageButton) findViewById(R.id.pad_numeric).findViewById(R.id.eq);

        if (mEqualButton == null || mEqualButton.getVisibility() != View.VISIBLE) {
            mEqualButton = (EqualsImageButton) findViewById(R.id.pad_operator).findViewById(R.id.eq);
        }

        mTokenizer = new CalculatorExpressionTokenizer(this);
        mEvaluator = new CalculatorExpressionEvaluator(mTokenizer);

        setState(CalculatorState.values()[
                savedInstanceState.getInt(KEY_CURRENT_STATE, CalculatorState.INPUT.ordinal())]);

        mFormulaEditText.setSolver(mEvaluator.getSolver());
        mFormulaEditText.setText(mTokenizer.getLocalizedExpression(
                savedInstanceState.getString(KEY_CURRENT_EXPRESSION, "")));
        mFormulaEditText.addTextChangedListener(mFormulaTextWatcher);
        mFormulaEditText.setOnKeyListener(mFormulaOnKeyListener);
        mFormulaEditText.setOnTextSizeChangeListener(this);
        mDeleteButton.setOnLongClickListener(this);
        findViewById(R.id.lparen).setOnLongClickListener(this);
        findViewById(R.id.rparen).setOnLongClickListener(this);
        findViewById(R.id.fun_sin).setOnLongClickListener(this);
        findViewById(R.id.fun_cos).setOnLongClickListener(this);
        findViewById(R.id.fun_tan).setOnLongClickListener(this);

        // Disable IME for this application
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM, WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        Button dot = (Button) findViewById(R.id.dec_point);
        dot.setText(String.valueOf(Constants.DECIMAL_POINT));
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Load up to date history
        mPersist = new Persist(this);
        mPersist.load();
        mHistory = mPersist.getHistory();
        incrementGroupId();

        // When history is open, the display is saved as a Display Entry. Cache it if it exists.
        HistoryEntry displayEntry = null;
        if (mHistoryAdapter != null) {
            displayEntry = mHistoryAdapter.getDisplayEntry();
        }

        // Create a new History Adapter (with the up-to-date history)
        mHistoryAdapter = new HistoryAdapter(this, mEvaluator.getSolver(), mHistory);
        mHistoryAdapter.setOnItemClickListener(new HistoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(final HistoryEntry entry) {
                mDisplayView.collapse(new AnimationFinishedListener() {
                    @Override
                    public void onAnimationFinished() {
                        if (mHistoryAdapter.hasGraph(entry.getFormula())) {
                            mFormulaEditText.setText(entry.getFormula());
                        } else {
                            mFormulaEditText.insert(entry.getResult());
                        }
                    }
                });
            }
        });
        mHistoryAdapter.setOnItemLongclickListener(new HistoryAdapter.OnItemLongClickListener() {
            @Override
            public void onItemLongClick(HistoryEntry entry) {
                Clipboard.copy(getBaseContext(), entry.getResult());
            }
        });

        // Restore the Display Entry (if it existed)
        if (displayEntry != null) {
            mHistoryAdapter.setDisplayEntry(displayEntry.getFormula(), displayEntry.getResult());
        }

        // Observe! Set! Typical adapter stuff.
        mHistory.setObserver(new History.Observer() {
            @Override
            public void notifyDataSetChanged() {
                mHistoryAdapter.notifyDataSetChanged();
            }
        });
        mDisplayView.setAdapter(mHistoryAdapter);
        mDisplayView.attachToRecyclerView(new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                if (viewHolder.getAdapterPosition() < mHistory.getEntries().size()) {
                    HistoryEntry item = mHistory.getEntries().get(viewHolder.getAdapterPosition());
                    mHistory.remove(item);
                    mHistoryAdapter.notifyItemRemoved(viewHolder.getAdapterPosition());
                } else {
                    mFormulaEditText.setText(null);
                }
                if (mHistory.getEntries().isEmpty()) {
                    mDisplayView.collapse();
                }
            }
        }));
        mDisplayView.scrollToMostRecent();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveHistory(mFormulaEditText.getCleanText(), TextUtil.getCleanText(mResultEditText, mEvaluator.getSolver()), true);
        mPersist.save();
    }

    protected boolean saveHistory(String expr, String result, boolean ensureResult) {
        if (mHistory == null) {
            return false;
        }

        expr = cleanExpression(expr);
        if (!ensureResult ||
                (!TextUtils.isEmpty(expr)
                        && !TextUtils.isEmpty(result)
                        && !Solver.equal(expr, result)
                        && (mHistory.current() == null || !mHistory.current().getFormula().equals(expr)))) {
            mHistory.enter(expr, result);
            return true;
        }
        return false;
    }

    protected String cleanExpression(String expr) {
        expr = EquationFormatter.appendParenthesis(expr);
        expr = Solver.clean(expr);
        expr = mTokenizer.getLocalizedExpression(expr);
        return expr;
    }

    protected String getLocalizedExpression(String expr) {
        return mTokenizer.getLocalizedExpression(expr);
    }

    protected String getNormalizedExpression(String expr) {
        return mTokenizer.getNormalizedExpression(expr);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        // If there's an animation in progress, cancel it first to ensure our state is up-to-date.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }

        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CURRENT_STATE, mCurrentState.ordinal());
        outState.putString(KEY_CURRENT_EXPRESSION,
                mTokenizer.getNormalizedExpression(mFormulaEditText.getCleanText()));
    }

    protected CalculatorState getState() {
        return mCurrentState;
    }

    protected void setState(CalculatorState state) {
        if (mCurrentState != state) {
            mCurrentState = state;
            invalidateEqualsButton();

            if (state == CalculatorState.RESULT || state == CalculatorState.ERROR) {
                mDeleteButton.setVisibility(View.GONE);
                mClearButton.setVisibility(View.VISIBLE);
            } else {
                mDeleteButton.setVisibility(View.VISIBLE);
                mClearButton.setVisibility(View.GONE);
            }

            if (state == CalculatorState.ERROR) {
                final int errorColor = getResources().getColor(R.color.calculator_error_color);
                mFormulaEditText.setTextColor(errorColor);
                mResultEditText.setTextColor(errorColor);
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    getWindow().setStatusBarColor(errorColor);
                }
            } else {
                mFormulaEditText.setTextColor(
                        getResources().getColor(R.color.display_formula_text_color));
                mResultEditText.setTextColor(
                        getResources().getColor(R.color.display_result_text_color));
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    getWindow().setStatusBarColor(
                            getResources().getColor(R.color.calculator_accent_color));
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mDisplayView.isExpanded()) {
            mDisplayView.collapse();
        } else if (mPadViewPager != null && mPadViewPager.isExpanded()) {
            mPadViewPager.collapse();
        } else {
            super.onBackPressed();
        }
    }

    public void onButtonClick(View view) {
        mCurrentButton = view;
        switch (view.getId()) {
            case R.id.eq:
                onEquals();
                break;
            case R.id.del:
                onDelete();
                break;
            case R.id.clr:
                onClear();
                break;
            case R.id.parentheses:
                mFormulaEditText.setText('(' + mFormulaEditText.getCleanText() + ')');
                break;
            case R.id.fun_sin:
            case R.id.fun_cos:
            case R.id.fun_tan:
                // Add left parenthesis after functions.
                insert(((LongClickButton) view).getText() + "(");
                break;
            case R.id.fun_ln:
            case R.id.fun_log:
            case R.id.fun_det:
            case R.id.fun_transpose:
            case R.id.fun_inverse:
            case R.id.fun_trace:
            case R.id.fun_norm:
            case R.id.fun_polar:
                // Add left parenthesis after functions.
                insert(((Button) view).getText() + "(");
                break;
            case R.id.op_add:
            case R.id.op_sub:
            case R.id.op_mul:
            case R.id.op_div:
            case R.id.op_fact:
            case R.id.op_pow:
                mFormulaEditText.insert(((Button) view).getText().toString());
                break;
            default:
                if(view instanceof LongClickButton)
                    insert(((LongClickButton) view).getText());
                else
                    insert(((Button) view).getText().toString());
                break;
        }
    }

    @Override
    public boolean onLongClick(View view) {
        mCurrentButton = view;
        switch (view.getId()) {
            case R.id.del:
                saveHistory(mFormulaEditText.getCleanText(), TextUtil.getCleanText(mResultEditText, mEvaluator.getSolver()), true);
                onClear();
                return true;
            case R.id.lparen:
            case R.id.rparen:
                mFormulaEditText.setText('(' + mFormulaEditText.getCleanText() + ')');
                return true;
            case R.id.fun_sin:
            case R.id.fun_cos:
            case R.id.fun_tan:
                insert(((LongClickButton) view).getAltText() + "(");
                return true;
        }
        return false;
    }

    /**
     * Inserts text into the formula EditText. If an equation was recently solved, it will
     * replace the formula's text instead of appending.
     */
    protected void insert(String text) {
        // Add left parenthesis after functions.
        if (mCurrentState.equals(CalculatorState.INPUT) ||
                mCurrentState.equals(CalculatorState.GRAPHING) ||
                mFormulaEditText.isCursorModified()) {
            mFormulaEditText.insert(text);
        } else {
            mFormulaEditText.setText(text);
            incrementGroupId();
        }
    }

    @Override
    public void onEvaluate(String expr, String result, int errorResourceId) {
        if (mCurrentState == CalculatorState.INPUT || mCurrentState == CalculatorState.GRAPHING) {
            if (result == null || Solver.equal(result, expr)) {
                mResultEditText.setText(null);
            } else {
                mResultEditText.setText(TextUtil.formatText(result, mFormulaEditText.getEquationFormatter(), mFormulaEditText.getSolver()));
            }
        } else if (errorResourceId != INVALID_RES_ID) {
            onError(errorResourceId);
        } else if (saveHistory(expr, result, true)) {
            mDisplayView.scrollToMostRecent();
            onResult(result);
        } else if (mCurrentState == CalculatorState.EVALUATE) {
            // The current expression cannot be evaluated -> return to the input state.
            setState(CalculatorState.INPUT);
        }
        invalidateEqualsButton();
    }

    protected void incrementGroupId() {
        mHistory.incrementGroupId();
    }

    protected void invalidateEqualsButton() {
        // Do nothing. Extensions of Basic Calculator may want to set the equals button to
        // Next mode during certain conditions.
    }

    @Override
    public void onTextSizeChanged(final TextView textView, float oldSize) {
        if (mCurrentState != CalculatorState.INPUT) { // TODO dont animate when showing graph
            // Only animate text changes that occur from user input.
            return;
        }

        // Calculate the values needed to perform the scale and translation animations,
        // maintaining the same apparent baseline for the displayed text.
        final float textScale = oldSize / textView.getTextSize();
        final float translationX;
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            translationX = (1.0f - textScale) *
                    (textView.getWidth() / 2.0f - textView.getPaddingEnd());
        } else {
            translationX = (1.0f - textScale) *
                    (textView.getWidth() / 2.0f - textView.getPaddingRight());
        }
        final float translationY = (1.0f - textScale) *
                (textView.getHeight() / 2.0f - textView.getPaddingBottom());
        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(textView, View.SCALE_X, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.SCALE_Y, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, translationX, 0.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, translationY, 0.0f));
        animatorSet.setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime));
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.start();
    }

    protected void onEquals() {
        String text = mFormulaEditText.getCleanText();
        if (mCurrentState == CalculatorState.INPUT) {
            switch (mEqualButton.getState()) {
                case EQUALS:
                    setState(CalculatorState.EVALUATE);
                    mEvaluator.evaluate(text, this);
                    break;
                case NEXT:
                    mFormulaEditText.next();
                    break;
            }
        } else if (mCurrentState == CalculatorState.GRAPHING) {
            setState(CalculatorState.EVALUATE);
            onEvaluate(text, "", INVALID_RES_ID);
        }
    }

    protected void onDelete() {
        // Delete works like backspace; remove the last character from the expression.
        mFormulaEditText.backspace();
    }

    private void reveal(View sourceView, int colorRes, final AnimatorListener listener) {
        // Make reveal cover the display
        final RevealView revealView = new RevealView(this);
        revealView.setLayoutParams(mLayoutParams);
        revealView.setRevealColor(getResources().getColor(colorRes));
        mDisplayForeground.addView(revealView);

        final SupportAnimator revealAnimator;
        final int[] clearLocation = new int[2];
        if (sourceView != null) {
            sourceView.getLocationInWindow(clearLocation);
            clearLocation[0] += sourceView.getWidth() / 2;
            clearLocation[1] += sourceView.getHeight() / 2;
        } else {
            clearLocation[0] = mDisplayForeground.getWidth() / 2;
            clearLocation[1] = mDisplayForeground.getHeight() / 2;
        }
        final int revealCenterX = clearLocation[0] - revealView.getLeft();
        final int revealCenterY = clearLocation[1] - revealView.getTop();
        final double x1_2 = Math.pow(revealView.getLeft() - revealCenterX, 2);
        final double x2_2 = Math.pow(revealView.getRight() - revealCenterX, 2);
        final double y_2 = Math.pow(revealView.getTop() - revealCenterY, 2);
        final float revealRadius = (float) Math.max(Math.sqrt(x1_2 + y_2), Math.sqrt(x2_2 + y_2));

        revealAnimator =
                ViewAnimationUtils.createCircularReveal(revealView,
                        revealCenterX, revealCenterY, 0.0f, revealRadius);
        revealAnimator.setDuration(
                getResources().getInteger(android.R.integer.config_longAnimTime));
        revealAnimator.addListener(listener);

        final Animator alphaAnimator = ObjectAnimator.ofFloat(revealView, View.ALPHA, 0.0f);
        alphaAnimator.setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime));
        alphaAnimator.addListener(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                mDisplayForeground.removeView(revealView);
            }
        });

        revealAnimator.addListener(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                play(alphaAnimator);
            }
        });
        play(revealAnimator);
    }

    protected void play(Animator animator) {
        mCurrentAnimator = animator;
        animator.addListener(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                mCurrentAnimator = null;
            }
        });
        animator.start();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
            mCurrentAnimator = null;
        }
    }

    protected void onClear() {
        if (TextUtils.isEmpty(mFormulaEditText.getCleanText())) {
            return;
        }
        reveal(mCurrentButton, R.color.calculator_accent_color, new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                mFormulaEditText.clear();
                incrementGroupId();
            }
        });
    }

    protected void onError(final int errorResourceId) {
        if (mCurrentState != CalculatorState.EVALUATE) {
            // Only animate error on evaluate.
            mResultEditText.setText(errorResourceId);
            return;
        }

        reveal(mCurrentButton, R.color.calculator_error_color, new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                setState(CalculatorState.ERROR);
                mResultEditText.setText(errorResourceId);
            }
        });
    }

    protected void onResult(final String result) {
        // Calculate the values needed to perform the scale and translation animations,
        // accounting for how the scale will affect the final position of the text.
        final float resultScale =
                mFormulaEditText.getVariableTextSize(result) / mResultEditText.getTextSize();
        final float resultTranslationX = (1.0f - resultScale) *
                (mResultEditText.getWidth() / 2.0f - mResultEditText.getPaddingRight());

        // Calculate the height of the formula (without padding)
        final float formulaRealHeight = mFormulaEditText.getHeight()
                - mFormulaEditText.getPaddingTop()
                - mFormulaEditText.getPaddingBottom();

        // Calculate the height of the resized result (without padding)
        final float resultRealHeight = resultScale *
                (mResultEditText.getHeight()
                        - mResultEditText.getPaddingTop()
                        - mResultEditText.getPaddingBottom());

        // Now adjust the result upwards!
        final float resultTranslationY =
                // Move the result up (so both formula + result heights match)
                -mFormulaEditText.getHeight()
                        // Now switch the result's padding top with the formula's padding top
                        - resultScale * mResultEditText.getPaddingTop()
                        + mFormulaEditText.getPaddingTop()
                        // But the result centers its text! And it's taller now! So adjust for that centered text
                        + (formulaRealHeight - resultRealHeight) / 2;

        // Move the formula all the way to the top of the screen
        final float formulaTranslationY = -mFormulaEditText.getBottom();

        // Use a value animator to fade to the final text color over the course of the animation.
        final int resultTextColor = mResultEditText.getCurrentTextColor();
        final int formulaTextColor = mFormulaEditText.getCurrentTextColor();
        final ValueAnimator textColorAnimator =
                ValueAnimator.ofObject(new ArgbEvaluator(), resultTextColor, formulaTextColor);
        textColorAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mResultEditText.setTextColor((Integer) valueAnimator.getAnimatedValue());
            }
        });
        mResultEditText.setText(TextUtil.formatText(result, mFormulaEditText.getEquationFormatter(), mFormulaEditText.getSolver()));
        mResultEditText.setPivotX(mResultEditText.getWidth() / 2);
        mResultEditText.setPivotY(0f);

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                textColorAnimator,
                ObjectAnimator.ofFloat(mResultEditText, View.SCALE_X, resultScale),
                ObjectAnimator.ofFloat(mResultEditText, View.SCALE_Y, resultScale),
                ObjectAnimator.ofFloat(mResultEditText, View.TRANSLATION_X, resultTranslationX),
                ObjectAnimator.ofFloat(mResultEditText, View.TRANSLATION_Y, resultTranslationY),
                ObjectAnimator.ofFloat(mFormulaEditText, View.TRANSLATION_Y, formulaTranslationY));
        animatorSet.setDuration(getResources().getInteger(android.R.integer.config_longAnimTime));
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.addListener(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                // Reset all of the values modified during the animation.
                mResultEditText.setPivotY(mResultEditText.getHeight() / 2);
                mResultEditText.setTextColor(resultTextColor);
                mResultEditText.setScaleX(1.0f);
                mResultEditText.setScaleY(1.0f);
                mResultEditText.setTranslationX(0.0f);
                mResultEditText.setTranslationY(0.0f);
                mFormulaEditText.setTranslationY(0.0f);

                // Finally update the formula to use the current result.
                mFormulaEditText.setText(result);
                setState(CalculatorState.RESULT);
            }
        });

        play(animatorSet);
    }

    protected CalculatorExpressionEvaluator getEvaluator() {
        return mEvaluator;
    }

    protected enum CalculatorState {
        INPUT, EVALUATE, RESULT, ERROR, GRAPHING
    }
}
