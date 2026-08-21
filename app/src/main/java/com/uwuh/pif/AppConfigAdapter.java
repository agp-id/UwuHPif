package com.uwuh.pif;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppConfigAdapter extends RecyclerView.Adapter<AppConfigAdapter.ViewHolder> {
    private List<AppModel> appList;
    private List<String> configOptions;
    private Context context;

    public AppConfigAdapter(Context context, List<AppModel> appList, List<String> configOptions) {
        this.context = context;
        this.appList = appList;
        this.configOptions = configOptions;
    }

    public void updateList(List<AppModel> newList) {
        this.appList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_config, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
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
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                item.setSelectedConfig(configOptions.get(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAppIcon;
        TextView tvAppName, tvPackageName;
        Spinner spConfigOption;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAppIcon = itemView.findViewById(R.id.imgAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvPackageName = itemView.findViewById(R.id.tvPackageName);
            spConfigOption = itemView.findViewById(R.id.spConfigOption);
        }
    }
}
