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
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class PublicKeysAdapter extends Adapter<PublicKeysAdapter.ViewHolder> {
    LayoutInflater inflater;
    ArrayList<PublicKey> publicKeys = new ArrayList<>();
    private final ClickListener listener;

    public PublicKeysAdapter(Context context, ClickListener listener) {
        inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view =inflater.inflate(R.layout.item_public_key,parent,false);
        ViewHolder holder = new ViewHolder(view, this.listener);
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

    public String getItemPublicKey(int position) {
        return publicKeys.get(position).getPublicKey();
    }

    public void addPublicKey(String coinType, Integer keyIndex, String publicKey, Integer image) {
        publicKeys.add(new PublicKey(coinType, keyIndex, publicKey, image));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private WeakReference<ClickListener> listenerRef;
        public LinearLayout llPublicKey;
        public TextView twCoinType;
        public TextView twPublicKey;
        public ImageView coinImage;

        public ViewHolder(View itemView, ClickListener listener)
        {
            super(itemView);

            listenerRef = new WeakReference<>(listener);

            llPublicKey = itemView.findViewById(R.id.llPublicKey);
            twCoinType = itemView.findViewById(R.id.text_coinType);
            //twKeyIndex = (TextView) itemView.findViewById(R.id.text_keyIndex);
            twPublicKey = itemView.findViewById(R.id.text_public_key);
            coinImage = itemView.findViewById(R.id.coin_image);

            llPublicKey.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listenerRef.get().onItemClicked(getAdapterPosition());
                }
            });
        }
    }

    public interface ClickListener {
        void onItemClicked(int position);
        void onItemLongClicked(int position);
    }

}
