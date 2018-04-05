package iot.vision.baktin.com.vision.facelist;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

import iot.vision.baktin.com.vision.R;

/**
 * Created by kevin.l.arnado on 04/04/2018.
 */

public class FaceListAdapter extends RecyclerView.Adapter<FaceListAdapter.MyViewHolder> {

    private static String TAG = FaceListAdapter.class.getCanonicalName();
    ArrayList<ImageItem> mList;
    Context context;

    public FaceListAdapter(Context context, ArrayList<ImageItem> list)
    {
//        Log.d(TAG,"FACELISTADAPTER CONTSTRUCTOR");
        this.context = context;
        mList = list;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(this.context).inflate(R.layout.image_row, parent, false);
//        Log.d(TAG,"ADAPTER ONCREATE VIEW: ");
        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
//        Log.d(TAG,"ADAPTER ONBIND: "+position);

        final ImageItem imagePerson = mList.get(position);
//        Log.d(TAG,"ADAPTER LABEL: "+imagePerson.getmName());
//        Log.d(TAG,"ADAPTER SIZE: "+mList.size());

        holder.mImage.setImageBitmap(imagePerson.getmImage());
        holder.mName.setText(imagePerson.getmName());
        holder.mDate.setText(imagePerson.getmDate());
    }


    @Override
    public int getItemCount() {
//        Log.d(TAG,"ADAPTER ITEM COUNT: "+mList.size());
        return mList.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder
    {
        ImageView mImage;
        TextView mName;
        TextView mDate;

        public MyViewHolder(View view)
        {
            super(view);
            mImage = view.findViewById(R.id.imgFace);
            mName = view.findViewById(R.id.txtName);
            mDate = view.findViewById(R.id.txtDate);
        }
    }
}
