package com.uwuh.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

public class AppConfigAdapter extends BaseAdapter {
    private List<AppModel> appList;
    private List<String> configOptions;
    private Context context;
    private LayoutInflater inflater;

    public AppConfigAdapter(Context context, List<AppModel> appList, List<String> configOptions) {
        this.context = context;
        this.appList = appList;
        this.configOptions = configOptions;
        this.inflater = LayoutInflater.from(context);
    }

    public void updateList(List<AppModel> newList) {
        this.appList = newList;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return appList.size();
    }

    @Override
    public Object getItem(int position) {
        return appList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_app_config, parent, false);
            holder = new ViewHolder();
            holder.imgAppIcon = convertView.findViewById(R.id.imgAppIcon);
            holder.tvAppName = convertView.findViewById(R.id.tvAppName);
            holder.tvPackageName = convertView.findViewById(R.id.tvPackageName);
            holder.spConfigOption = convertView.findViewById(R.id.spConfigOption);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        AppModel item = appList.get(position);
        holder.tvAppName.setText(item.getAppName());
        holder.tvPackageName.setText(item.getPackageName());
        if (item.getIcon() != null) {
            holder.imgAppIcon.setImageDrawable(item.getIcon());
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, configOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.spConfigOption.setAdapter(spinnerAdapter);

        int selectedIndex = configOptions.indexOf(item.getSelectedConfig());
        if (selectedIndex >= 0) {
            holder.spConfigOption.setSelection(selectedIndex);
        } else {
            holder.spConfigOption.setSelection(0); // "None"
        }

        holder.spConfigOption.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                item.setSelectedConfig(configOptions.get(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });

        return convertView;
    }

    private static class ViewHolder {
        ImageView imgAppIcon;
        TextView tvAppName, tvPackageName;
        Spinner spConfigOption;
    }
}
