import mongoose from "mongoose";

const albumSchema = new mongoose.Schema({
    userId : {
        type: String,
        required: true,
    },
    name : {
        type: String,
        required: true,
    },
    description : {
        type: String,
    },
    coverPhotoUrl : {
        type: String,
    }
})

const Album = mongoose.models.Album || mongoose.model("Album", albumSchema);

export default Album;