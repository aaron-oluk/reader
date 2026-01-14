package com.pdfreader.app;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class SignatureAdapter extends RecyclerView.Adapter<SignatureAdapter.SignatureViewHolder> {
    
    private List<String> signaturePaths;
    private SignatureManager signatureManager;
    private OnSignatureClickListener clickListener;
    private OnSignatureDeleteListener deleteListener;
    
    public interface OnSignatureClickListener {
        void onSignatureClick(String filePath);
    }
    
    public interface OnSignatureDeleteListener {
        void onSignatureDelete(String filePath);
    }
    
    public SignatureAdapter(List<String> signaturePaths, SignatureManager signatureManager) {
        this.signaturePaths = signaturePaths;
        this.signatureManager = signatureManager;
    }
    
    public void setOnSignatureClickListener(OnSignatureClickListener listener) {
        this.clickListener = listener;
    }
    
    public void setOnSignatureDeleteListener(OnSignatureDeleteListener listener) {
        this.deleteListener = listener;
    }
    
    @NonNull
    @Override
    public SignatureViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_signature, parent, false);
        return new SignatureViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull SignatureViewHolder holder, int position) {
        String filePath = signaturePaths.get(position);
        String name = signatureManager.getSignatureName(filePath);
        
        holder.signatureName.setText(name);
        
        // Load signature preview
        Bitmap signature = signatureManager.loadSignature(filePath);
        if (signature != null) {
            holder.signaturePreview.setImageBitmap(signature);
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onSignatureClick(filePath);
            }
        });
        
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onSignatureDelete(filePath);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return signaturePaths.size();
    }
    
    public void updateSignatures(List<String> newSignatures) {
        this.signaturePaths = newSignatures;
        notifyDataSetChanged();
    }
    
    static class SignatureViewHolder extends RecyclerView.ViewHolder {
        ImageView signaturePreview;
        TextView signatureName;
        MaterialButton btnDelete;
        
        SignatureViewHolder(@NonNull View itemView) {
            super(itemView);
            signaturePreview = itemView.findViewById(R.id.signature_preview);
            signatureName = itemView.findViewById(R.id.signature_name);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
