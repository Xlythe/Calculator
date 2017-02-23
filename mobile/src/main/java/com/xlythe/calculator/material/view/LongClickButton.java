package com.xlythe.calculator.material.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.xlythe.calculator.material.R;

/**
 * @author Emmanuel
 *         on 22/2/2017, at 06:41.
 */

public class LongClickButton extends RelativeLayout {
	private String mText, mAltText;
    private float altTextSize;
    private static int mTextStyle = R.style.PadButtonStyle_Advanced;

	public LongClickButton(Context context) {
		super(context);
        init(context, null, 0);
    }

	public LongClickButton(Context context, AttributeSet attrs) {
		super(context, attrs);
        init(context, attrs, 0);
    }

    public LongClickButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.LongClickButton,
                defStyle, R.style.PadButtonStyle_LongClick);

        try {
            mText = a.getString(R.styleable.LongClickButton_text);
            mAltText = a.getString(R.styleable.LongClickButton_altText);
            altTextSize = a.getDimension(R.styleable.LongClickButton_altTextSize, 0);
            mTextStyle = a.getResourceId(R.styleable.LongClickButton_textStyle, mTextStyle);
        } finally {
            a.recycle();
        }

        LayoutInflater.from(context).inflate(R.layout.button_longtap, this);
        setPadding(0,0,0,0);

        TextView b = ((TextView) findViewById(R.id.text)),
                alt = ((TextView) findViewById(R.id.alt_text));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            b.setTextAppearance(mTextStyle);
            alt.setTextAppearance(mTextStyle);
        } else {
            b.setTextAppearance(context, mTextStyle);
            alt.setTextAppearance(context, mTextStyle);
        }

        alt.setTextSize(altTextSize);

        b.setText(mText);
        alt.setText(mAltText);
    }

	public String getText() {
		return mText;
	}

    public String getAltText() {
        return mAltText;
    }

}
