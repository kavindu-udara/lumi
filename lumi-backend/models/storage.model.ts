import mongoose from "mongoose";

const storageSchema = new mongoose.Schema({
    userId : {
        type: String,
        required: true,
    },
    planId : {
        type: mongoose.Schema.Types.ObjectId,
        ref: "Plan",
        required: true,
    }, 
    usedStorage : {
        type: Number,
        default: 0,
    },
})

const Storage = mongoose.models.Storage || mongoose.model("Storage", storageSchema);

export default Storage;
