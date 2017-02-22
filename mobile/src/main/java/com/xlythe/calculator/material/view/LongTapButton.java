package com.xlythe.calculator.material.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.xlythe.calculator.material.R;

/**
 * @author Emmanuel
 *         on 22/2/2017, at 06:41.
 */

public class LongTapButton extends RelativeLayout {

	private String mText, mAltText;

	public LongTapButton(Context context) {
		super(context);
        LayoutInflater.from(context).inflate(R.layout.button_longtap, this);
    }

	public LongTapButton(Context context, AttributeSet attrs) {
		super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.LongTapButton, 0, 0);

		try {
            mText = a.getString(R.styleable.LongTapButton_text);
            mAltText = a.getString(R.styleable.LongTapButton_altText);

		} finally {
			a.recycle();
		}

        LayoutInflater.from(context).inflate(R.layout.button_longtap, this);


        ((Button) findViewById(R.id.button)).setText(mText);
        ((TextView) findViewById(R.id.alt_text)).setText(mAltText);
	}

	public LongTapButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.button_longtap, this);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public LongTapButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
        LayoutInflater.from(context).inflate(R.layout.button_longtap, this);
    }

	public String getText() {
		return mText;
	}

}
