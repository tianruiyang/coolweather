package com.coolweather.android;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coolweather.android.db.City;
import com.coolweather.android.db.County;
import com.coolweather.android.db.Province;
import com.coolweather.android.util.HttpUtil;
import com.coolweather.android.util.Utilty;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.internal.Util;

public class ChooseAreaFragment extends Fragment {
    public static final int LEVEL_PROVINCE = 0;

    public static final int LEVEL_CITY = 1;

    public static final int LEVEL_COUNTY = 2;

    private ProgressDialog progressDialog;

    private TextView titleText;

    private Button backButton;

    private ListView listView;

    private ArrayAdapter<String> adapter;

    private List<String> dataList = new ArrayList<>();
    //省列表
    private List<Province> provinceList;
    //市列表
    private List<City> cityList;
    //县列表
    private List<County> countyList;
    //选中的省份
    private Province selectedProvince;
    //选中的城市
    private City selectedCity;
    //当前选中级别
    private int currentLevel;

    @Nullable
    @Override
    @TargetApi(23)
    //碎片生命周期阶段
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area,container,false);
        titleText = view.findViewById(R.id.title_text);
        backButton = view.findViewById(R.id.back_button);
        listView = (ListView)view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getContext(),android.R.layout.simple_list_item_1,dataList);
        listView.setAdapter(adapter);
        return view;
    }
    //碎片生命周期阶段

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(currentLevel == LEVEL_PROVINCE){
                    selectedProvince = provinceList.get(position);
                    queryCities();
                }
                else if(currentLevel == LEVEL_CITY){
                    selectedCity = cityList.get(position);
                    queryCounties();
                }else if(currentLevel == LEVEL_COUNTY){
                    String weatherId = countyList.get(position).getWeatherId();
                    //判断在哪个Activity里

                    if(getActivity() instanceof MainActivity){
                        Intent intent = new Intent(getActivity(),WeatherActivity.class);
                        intent.putExtra("weather_id",weatherId);
                        startActivity(intent);
                        getActivity().finish();
                    }else if(getActivity() instanceof WeatherActivity){
                        WeatherActivity activity = (WeatherActivity) getActivity();
                        activity.drawerLayout.closeDrawers();
                        activity.swipeRefresh.setRefreshing(true);
                        activity.requestWeather(weatherId);

                    }



                }
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(currentLevel == LEVEL_COUNTY){
                    queryCities();
                }
                else if(currentLevel == LEVEL_CITY){
                    queryProvinces();
                }
            }
        });
        queryProvinces();
    }

    //优先从数据库查询所有的省，查不到就去服务器查询
    private void queryProvinces() {
        titleText.setText("中国");
        backButton.setVisibility(View.GONE);
        provinceList = DataSupport.findAll(Province.class);
        if (provinceList.size() > 0) {
            dataList.clear();
            for (Province province : provinceList) {
                dataList.add(province.getProviceName());
            }

            //提示更新数据
            adapter.notifyDataSetChanged();
            //显示结果从1开始
            listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        }
        else{
            //从服务器上获取
            String address = "http://guolin.tech/api/china";
            queryFromServer(address,"province");

        }
    }

    /*
        优先查询数据库中所有的县
     */
    private void queryCities(){
        titleText.setText(selectedProvince.getProviceName());
        backButton.setVisibility(View.VISIBLE);
        cityList = DataSupport.where("provinceid = ?",String.valueOf(selectedProvince.getId())).find(City.class);
        if (cityList.size() > 0) {
            dataList.clear();
            for (City city : cityList) {
                dataList.add(city.getCityName());
            }

            //提示更新数据
            adapter.notifyDataSetChanged();
            //显示结果从1开始
            listView.setSelection(0);
            currentLevel = LEVEL_CITY;
        }
        else{
            //从服务器上获取
            String address = "http://guolin.tech/api/china"+"/"+selectedProvince.getProviceCode();
            queryFromServer(address,"city");

        }

    }
    private void queryCounties(){
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        countyList = DataSupport.where("cityid = ?",String.valueOf(selectedCity.getId())).find(County.class);
        Log.e("number",""+countyList.size());
        if (countyList.size() > 0) {
            dataList.clear();
            for (County county : countyList) {
                dataList.add(county.getCountyName());
            }

            //提示更新数据
            adapter.notifyDataSetChanged();
            //显示结果从1开始
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        }
        else{
            //从服务器上获取
            String address = "http://guolin.tech/api/china" + "/" + selectedProvince.getProviceCode() + "/" +selectedCity.getCityCode();
            queryFromServer(address,"county");

        }
    }

    //从服务器上获取数据
    private void queryFromServer(final String address, final String type){
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                closeProgressDialog();
                Toast.makeText(getActivity(),"加载失败",Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                //获取Response中的所有数据
                String responseText = response.body().string();
                //判断是否有结果
                boolean result = false;
                if("province".equals(type)){
                    result = Utilty.handleProvinceResponse(responseText);
                }
                else if("city".equals(type)){
                    result = Utilty.handleCityResponse(responseText,selectedProvince.getId());
                }
                else if("county".equals(type)){
                    result = Utilty.handleCountyResponse(responseText,selectedCity.getId());
                }
                if(result){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            /*switch(type){
                                case "province":
                                    queryProvinces();
                                    break;
                                case "city":
                                    queryCities();
                                    break;
                                case "county":
                                    queryCounties();
                                    break;
                                    default:break;
                            }*/
                            if("province".equals(type))
                                queryProvinces();
                            else if("city".equals(type))
                                queryCities();
                            else if("county".equals(type))
                                queryCounties();
                        }
                    });
                }
            }
        });
    }

    private void showProgressDialog(){
        if(progressDialog == null){
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载...");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }

    private void closeProgressDialog(){
        if(progressDialog != null)
            progressDialog.dismiss();
    }

}
