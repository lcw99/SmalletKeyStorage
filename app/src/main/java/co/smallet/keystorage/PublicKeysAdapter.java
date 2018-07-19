package co.smallet.keystorage;

import android.content.Context;
import android.media.Image;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class PublicKeysAdapter extends Adapter<PublicKeysAdapter.ViewHolder> {
    LayoutInflater inflater;
    ArrayList<PublicKey> publicKeys = new ArrayList<>();

    public PublicKeysAdapter(Context context){
        inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view =inflater.inflate(R.layout.item_public_key,parent,false);
        ViewHolder holder = new ViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.twCoinType.setText(publicKeys.get(position).getCoinType() + "(" +publicKeys.get(position).getKeyIndex().toString() + ")");
        holder.twPublicKey.setText(publicKeys.get(position).getPublicKey());
        int imageId = publicKeys.get(position).getImage();
        if (imageId != 0)
            holder.coinImage.setImageResource(imageId);
    }

    @Override
    public int getItemCount() {
        return publicKeys.size();
    }

    public void addPublicKey(String coinType, Integer keyIndex, String publicKey, Integer image) {
        publicKeys.add(new PublicKey(coinType, keyIndex, publicKey, image));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        // Your holder should contain a member variable
        // for any view that will be set as you render a row
        public TextView twCoinType;
        //public TextView twKeyIndex;
        public TextView twPublicKey;
        public ImageView coinImage;

        // We also create a constructor that accepts the entire item row
        // and does the view lookups to find each subview
        public ViewHolder(View itemView) {
            // Stores the itemView in a public final member variable that can be used
            // to access the context from any ViewHolder instance.
            super(itemView);

            twCoinType = itemView.findViewById(R.id.text_coinType);
            //twKeyIndex = (TextView) itemView.findViewById(R.id.text_keyIndex);
            twPublicKey = itemView.findViewById(R.id.text_public_key);
            coinImage = itemView.findViewById(R.id.coin_image);

        }
    }
}
