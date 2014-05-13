package org.askerov.dynamicgrid.example;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;
import org.askerov.dynamicgrid.DynamicGridView;
import org.askerov.dynamicgrid.DynamicGridView.ItemHoverListener;

import java.util.ArrayList;
import java.util.Arrays;

public class GridActivity extends Activity implements ItemHoverListener {

    private DynamicGridView gridView;
	private View mobileView;
	private View targetView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grid);
        gridView = (DynamicGridView) findViewById(R.id.dynamic_grid);
        gridView.setAdapter(new CheeseDynamicAdapter(this,
                new ArrayList<String>(Arrays.asList(Cheeses.sCheeseStrings)),
                3));
        gridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                gridView.startEditMode();
                return false;
            }
        });

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(GridActivity.this, parent.getAdapter().getItem(position).toString(),
                        Toast.LENGTH_SHORT).show();
            }
        });
        gridView.setIsReorder(false);
        gridView.setItemHoverListener(this);
    }

    @Override
    public void onBackPressed() {
        if (gridView.isEditMode()) {
            gridView.stopEditMode();
        } else {
            super.onBackPressed();
        }
    }

	@Override
	public void onItemHoverStart(View mobileView, View targetView) {
		this.mobileView = mobileView;
		this.targetView = targetView;
		mobileView.setBackgroundColor(getResources().getColor(R.color.AliceBlue));
		targetView.setBackgroundColor(getResources().getColor(R.color.FireBrick));
		
	}

	@Override
	public void onItemHoverStop() {
		if(mobileView != null)
		mobileView.setBackgroundColor(getResources().getColor(R.color.transparent));
		if(targetView != null)
		targetView.setBackgroundColor(getResources().getColor(R.color.transparent));
		mobileView = targetView = null;
	}
}
