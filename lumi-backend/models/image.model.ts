import mongoose from "mongoose";

const imageSchema = new mongoose.Schema({
    userId : {
        type: String,
        required: true,
    },
    imageId : {
        type: String,
        required: true,
    },
    location : {
        latitude : {
            type: Number,
        },
        longitude : {
            type: Number,
        }
    },
    albumId : {
        type: mongoose.Schema.Types.ObjectId,
        ref: "Album",
        required: true,
    },
    size : {
        type: Number,
    },
    metadata : [{
        name: {
            type: String,
        },
        type: {
            type: String,
        },
    }],
    timestamp : {
        type: Date,
        default: Date.now,
    },
})

if (process.env.NODE_ENV !== "production" && mongoose.models.Image) {
    delete mongoose.models.Image;
}

const Image = mongoose.models.Image || mongoose.model("Image", imageSchema);

export default Image;
