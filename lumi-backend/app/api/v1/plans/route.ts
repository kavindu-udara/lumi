import Plan from "@/models/plans.model";
import { NextRequest } from "next/server";

export async function GET(request : NextRequest){
    try{
        const plans = await Plan.find().select("-__v").lean();
        return Response.json({ plans }, { status: 200 });
    }catch(error){
        const message = error instanceof Error ? error.message : "Internal server error";
        return Response.json({ error: message }, { status: 500 });
    }
}
