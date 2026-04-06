import mongoose from "mongoose";

const subscriptionSchema = new mongoose.Schema({
    userId : {
        type: String,
        required: true,
    },
    planId : {
        type: mongoose.Schema.Types.ObjectId,
        ref: "Plan",
        required: true,
    },
    stripeMerchantId : {
        type: String,
        default: null,
    },
    paymentIntentId : {
        type: String,
    },
    startDate : {
        type: Date,
        default: Date.now,
    },
    endDate : {
        type: Date,
    },
})

const Subscription = mongoose.models.Subscription || mongoose.model("Subscription", subscriptionSchema);

export default Subscription;
