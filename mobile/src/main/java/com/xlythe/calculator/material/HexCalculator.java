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

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.xlythe.calculator.material.CalculatorExpressionEvaluator.EvaluateCallback;
import com.xlythe.calculator.material.util.TextUtil;
import com.xlythe.calculator.material.view.FormattedNumberEditText;
import com.xlythe.math.Base;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Adds graphing and base switching to the basic calculator.
 */
public abstract class HexCalculator extends PanelSwitchingCalculator {

    // instance state keys
    private static final String KEY_BASE = NAME + "_base";

    private FormattedNumberEditText mFormulaEditText;
    private TextView mResultEditText;

    private TextView mInfoView;
    private boolean mShowBaseDetails;
    private boolean mShowTrigDetails;
    private NumberBaseManager mBaseManager;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_BASE, mBaseManager.getNumberBase().ordinal());
    }

    @Override
    protected void initialize(Bundle savedInstanceState) {
        super.initialize(savedInstanceState);
        mInfoView = (TextView) findViewById(R.id.info);
        mFormulaEditText = (FormattedNumberEditText) findViewById(R.id.formula);
        mResultEditText = (TextView) findViewById(R.id.result);

        Base base = Base.DECIMAL;
        int baseOrdinal = savedInstanceState.getInt(KEY_BASE, -1);
        if (baseOrdinal != -1) {
            base = Base.values()[baseOrdinal];
        }
        mBaseManager = new NumberBaseManager(base);
        invalidateSelectedBase(base);

        mShowBaseDetails = !mBaseManager.getNumberBase().equals(Base.DECIMAL);
        mShowTrigDetails = false;

        invalidateDetails();
    }

    @Override
    public void onButtonClick(View view) {
        switch (view.getId()) {
            case R.id.fun_cos:
            case R.id.fun_sin:
            case R.id.fun_tan:
                mShowTrigDetails = true;
                invalidateDetails();
                break;
            case R.id.hex:
                setBase(Base.HEXADECIMAL);
                return;
            case R.id.bin:
                setBase(Base.BINARY);
                return;
            case R.id.dec:
                setBase(Base.DECIMAL);
                return;
        }
        super.onButtonClick(view);
    }

    @Override
    public boolean onLongClick(View view) {
        switch (view.getId()) {
            case R.id.fun_cos:
            case R.id.fun_sin:
            case R.id.fun_tan:
                mShowTrigDetails = true;
                invalidateDetails();
                break;
        }
        return super.onLongClick(view);
    }

    private void setBase(Base base) {
        // Update the BaseManager, which handles restricting which buttons to show
        mBaseManager.setNumberBase(base);
        mShowBaseDetails = true;

        // Update the evaluator, which handles the math
        getEvaluator().setBase(mFormulaEditText.getCleanText(), base, new EvaluateCallback() {
            @Override
            public void onEvaluate(String expr, String result, int errorResourceId) {
                if (errorResourceId != INVALID_RES_ID) {
                    onError(errorResourceId);
                } else {
                    mResultEditText.setText(TextUtil.formatText(result, mFormulaEditText.getEquationFormatter(), mFormulaEditText.getSolver()));
                    onResult(result);
                }
            }
        });
        invalidateSelectedBase(base);
    }

    private void invalidateSelectedBase(Base base) {
        setSelectedBaseButton(base);

        // Disable any buttons that are not relevant to the current base
        for (int resId : mBaseManager.getViewIds()) {
            View view = findViewById(resId);
            if (view != null) {
                view.setEnabled(!mBaseManager.isViewDisabled(resId));
            }
        }

        invalidateDetails();
    }

    private void setSelectedBaseButton(Base base) {
        findViewById(R.id.hex).setSelected(base.equals(Base.HEXADECIMAL));
        findViewById(R.id.bin).setSelected(base.equals(Base.BINARY));
        findViewById(R.id.dec).setSelected(base.equals(Base.DECIMAL));
    }

    protected void invalidateDetails() {
        List<Detail> details = getDetails();

        String text = "";
        for (Detail detail : details) {
            if (!text.isEmpty()) {
                text += " | ";
            }
            text += detail.word;
        }

        if (mInfoView != null) {
            mInfoView.setMovementMethod(LinkMovementMethod.getInstance());
            mInfoView.setText(text, TextView.BufferType.SPANNABLE);
            for (Detail detail : details) {
                if (detail.listener != null) {
                    setClickableSpan(mInfoView, detail.word, detail.listener);
                }
            }
        }
    }

    protected List<Detail> getDetails() {
        List<Detail> details = new LinkedList<>();
        if (mShowBaseDetails) {
            details.add(getBaseDetail());
        }
        if (mShowTrigDetails) {
            details.add(getUnitDetail());
        }
        return details;
    }

    private Detail getBaseDetail() {
        String text = "";
        switch (mBaseManager.getNumberBase()) {
            case HEXADECIMAL:
                text = getString(R.string.hex).toUpperCase(Locale.getDefault());
                break;
            case BINARY:
                text = getString(R.string.bin).toUpperCase(Locale.getDefault());
                break;
            case DECIMAL:
                text = getString(R.string.dec).toUpperCase(Locale.getDefault());
                break;
        }

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int DEC = 0;
                final int HEX = 1;
                final int BIN = 2;
                final PopupMenu popupMenu = new PopupMenu(HexCalculator.this, mInfoView);
                final Menu menu = popupMenu.getMenu();
                menu.add(0, DEC, menu.size(), R.string.desc_dec);
                menu.add(0, HEX, menu.size(), R.string.desc_hex);
                menu.add(0, BIN, menu.size(), R.string.desc_bin);
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case DEC:
                                setBase(Base.DECIMAL);
                                break;
                            case HEX:
                                setBase(Base.HEXADECIMAL);
                                break;
                            case BIN:
                                setBase(Base.BINARY);
                                break;
                        }
                        return true;
                    }
                });
                popupMenu.show();
            }
        };
        return new Detail(text, listener);
    }

    private Detail getUnitDetail() {
        String text = CalculatorSettings.useRadians(getBaseContext()) ?
                getString(R.string.radians) : getString(R.string.degrees);

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int RAD = 0;
                final int DEG = 1;
                final PopupMenu popupMenu = new PopupMenu(HexCalculator.this, mInfoView);
                final Menu menu = popupMenu.getMenu();
                menu.add(0, RAD, menu.size(), R.string.radians);
                menu.add(0, DEG, menu.size(), R.string.degrees);
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case RAD:
                                CalculatorSettings.setRadiansEnabled(getBaseContext(), true);
                                break;
                            case DEG:
                                CalculatorSettings.setRadiansEnabled(getBaseContext(), false);
                                break;
                        }
                        invalidateDetails();
                        if (getState() != CalculatorState.GRAPHING) {
                            setState(CalculatorState.INPUT);
                        }
                        getEvaluator().evaluate(mFormulaEditText.getCleanText(), HexCalculator.this);
                        return true;
                    }
                });
                popupMenu.show();
            }
        };
        return new Detail(text, listener);
    }

    private void setClickableSpan(TextView textView, final String word, final View.OnClickListener listener) {
        final Spannable spans = (Spannable) textView.getText();
        String text = spans.toString();
        ClickableSpan span = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Selection.setSelection(spans, 0);
                listener.onClick(null);
            }

            public void updateDrawState(@NonNull TextPaint ds) {
            }
        };
        spans.setSpan(span, text.indexOf(word), text.indexOf(word) + word.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public static class Detail {
        public final String word;
        public final View.OnClickListener listener;

        public Detail(String word, View.OnClickListener listener) {
            this.word = word;
            this.listener = listener;
        }
    }
}
