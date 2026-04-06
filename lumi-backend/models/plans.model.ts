import mongoose from "mongoose";

const planSchema = new mongoose.Schema({
    name : {
        type: String,
        required: true,
    },
    storageLimit : {
        type: Number,
        required: true,
    },
    price : {
        type: Number,
        required: true,
    },
})

const Plan = mongoose.models.Plan || mongoose.model("Plan", planSchema);

export default Plan;
