package com.xlythe.calculator.holo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.GridView;
import android.widget.ListAdapter;

import com.xlythe.calculator.holo.dao.App;
import com.xlythe.dao.RemoteModel;
import com.xlythe.engine.theme.Theme;

import java.lang.reflect.Method;
import java.util.List;

public class ThemesFragment extends Fragment implements OnItemClickListener, OnItemLongClickListener {
    private static final String EXTRA_LIST_POSITION = "list_position";
    private static final String EXTRA_LIST_VIEW_OFFSET = "list_view_top";

    private GridView mGridView;
    private List<App> mThemes;

    @Override
    public View inflateView(Bundle savedInstanceState) {
        // Create the GridView
        mGridView = new GridView(getActivity());
        mGridView.setOnItemClickListener(this);
        mGridView.setOnItemLongClickListener(this);
        mGridView.setNumColumns(GridView.AUTO_FIT);
        mGridView.setGravity(Gravity.CENTER);
        mGridView.setColumnWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 125 + 30, getActivity().getResources().getDisplayMetrics()));
        mGridView.setStretchMode(GridView.STRETCH_SPACING_UNIFORM);

        // Load the cache
        mThemes = new App.Query(getContext()).all();

        // Show ui
        setListAdapter(new StoreAdapter(getActivity(), mThemes));

        return mGridView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mThemes.isEmpty()) setViewShown(false);
        new App.Query(getContext()).all(new RemoteModel.Callback<List<App>>() {
            @Override
            public void onSuccess(List<App> result) {
                mThemes.clear();
                mThemes.addAll(result);
                if (!isDetached()) {
                    ((StoreAdapter) getListAdapter()).notifyDataSetChanged();
                    setViewShown(true);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {

            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        // Restore the scroll position, if any
        final Bundle args = getArguments();
        if (args != null) {
            mGridView.setSelection(args.getInt(EXTRA_LIST_POSITION, 0));
            // Hack to scroll to the previous offset
            mGridView.post(new Runnable() {
                @Override
                public void run() {
                    if (android.os.Build.VERSION.SDK_INT >= 19) {
                        mGridView.scrollListBy(-1 * args.getInt(EXTRA_LIST_VIEW_OFFSET, 0));
                    } else {
                        try {
                            Method m = AbsListView.class.getDeclaredMethod("trackMotionScroll", Integer.TYPE, Integer.TYPE);
                            m.setAccessible(true);
                            m.invoke(mGridView, args.getInt(EXTRA_LIST_VIEW_OFFSET, 0), args.getInt(EXTRA_LIST_VIEW_OFFSET, 0));
                        } catch (Exception e) {
                        }
                    }
                }
            });
        }
    }

    public ListAdapter getListAdapter() {
        return mGridView.getAdapter();
    }

    public void setListAdapter(ListAdapter adapter) {
        mGridView.setAdapter(adapter);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        onListItemClick(position);
    }

    public void onListItemClick(int position) {
        if (App.doesPackageExists(getContext(), mThemes.get(position).getPackageName())) {
            String appName = mThemes.get(position).getPackageName();

            // Update theme
            CalculatorSettings.setTheme(getContext(), appName);
            Theme.setPackageName(appName);

            // Create a new intent to relaunch the settings
            Intent intent = new Intent(getActivity(), getActivity().getClass());

            // Preserve the list offsets
            int itemPosition = mGridView.getFirstVisiblePosition();
            View child = mGridView.getChildAt(0);
            int itemOffset = child != null ? child.getTop() : 0;

            intent.putExtra(EXTRA_LIST_POSITION, itemPosition);
            intent.putExtra(EXTRA_LIST_VIEW_OFFSET, itemOffset);

            // Go
            getActivity().startActivity(intent);
            getActivity().finish();

            // Set a smooth fade transition
            getActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String customUrl = mThemes.get(position).getCustomUrl();
            intent.setData(Uri.parse(customUrl != null && !customUrl.isEmpty()
                    ? customUrl : "market://details?id=" + mThemes.get(position).getPackageName()));
            getActivity().startActivity(intent);
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        return onListItemLongClick(position);
    }

    public boolean onListItemLongClick(int position) {
        if (App.doesPackageExists(getContext(), mThemes.get(position).getPackageName())) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String customUrl = mThemes.get(position).getCustomUrl();
            intent.setData(Uri.parse(customUrl != null && !customUrl.isEmpty()
                    ? customUrl : "market://details?id=" + mThemes.get(position).getPackageName()));
            startActivity(intent);
            return true;
        }
        return false;
    }
}
