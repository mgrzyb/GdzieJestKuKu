import {Firestore} from "firebase-admin/firestore";
import {firestore} from "firebase-admin";
import DocumentReference = firestore.DocumentReference;

interface GeoLocationRequestBase {
    id : string,
    recipientToken : string,
    senderToken : string,
    createdOn : Date,
    logs: { timestamp : Date, message : string }[]
}

interface PendingGeoLocationRequest extends GeoLocationRequestBase {
    status : 'new' | 'sent' | 'received';
}
interface FullfiledGeoLocationRequest extends GeoLocationRequestBase {
    status : 'fulfilled'
    location: { lat : number, lon: number }
    locationTimestamp : Date
}

type GeoLocationRequest = PendingGeoLocationRequest | FullfiledGeoLocationRequest;

function getGeoLocationRequestId(senderToken : string, recipientToken : string) {
    return `${senderToken}:${recipientToken}`;
}

function getGeoLocationRequestDocRef(firestore: Firestore, requestId : string) {
    const requests = firestore.collection('geo-location-requests');
    return requests.doc(requestId);
}

export async function getGeoLocationRequest(firestore: Firestore, requestId: string) : Promise<GeoLocationRequest>{
    const docRef = getGeoLocationRequestDocRef(firestore, requestId);
    const r = await docRef.get();
    return r.data() as GeoLocationRequest;
}

async function getGeoLocationRequestRef(firestore: Firestore, requestId: string) : Promise<[DocumentReference, GeoLocationRequest]>{
    const docRef = getGeoLocationRequestDocRef(firestore, requestId);
    const r = await docRef.get();
    const request = r.data() as GeoLocationRequest;
    return [docRef, request];
}

export async function getOrCreateGeoLocationRequest(firestore: Firestore, senderToken: string, recipientToken: string) {
    let requestId = getGeoLocationRequestId(senderToken, recipientToken);
    const docRef = getGeoLocationRequestDocRef(firestore, requestId);

    const newRequest: PendingGeoLocationRequest = {
        id : requestId,
        senderToken: senderToken,
        recipientToken: recipientToken,
        createdOn: new Date(),
        status: 'new',
        logs: [
            { timestamp: new Date(), message: "GeoLocation requested" }
        ]
    }
    
    try {
        await docRef.create(newRequest);
        return newRequest;
    } catch (e) {
        const result = await docRef.get();
        const r = result.data() as GeoLocationRequest;
        if (r.status === "fulfilled"){
            if ((Number(new Date()) - Number(r.createdOn))/1000/60 > 5) { // 5 minutes
                await docRef.set(newRequest);
                return newRequest;
            }
        }
        r.logs.push(
            { timestamp: new Date(), message: "GeoLocation requested" });
        await docRef.set(r);
        return r;
    }
}

export async function markGeoLocationRequestAsSent(firestore: Firestore, requestId: string, messageId : string) {
    const [docRef, request] = await getGeoLocationRequestRef(firestore, requestId);
    
    if (request.status === 'new')
        request.status = 'sent';
    
    request.logs.push({
        timestamp: new Date(),
        message: `Request notification sent (messageId: ${messageId})`
    })
    
    await docRef.set(request);
    return request;
}

export async function markGeoLocationRequestAsReceived(firestore: Firestore, requestId: string) {
    const [docRef, request] = await getGeoLocationRequestRef(firestore, requestId);

    if (request.status === 'sent')
        request.status = 'received';
    
    request.logs.push({
        timestamp: new Date(),
        message: 'Request notification received'
    })

    await docRef.set(request);
    return request;
}

export async function fulfillGeoLocationRequest(firestore: Firestore, requestId: string, location: {
    lat: number,
    lon: number
}) {
    const [docRef, request] = await getGeoLocationRequestRef(firestore, requestId);

    const f = request as unknown as FullfiledGeoLocationRequest;
    f.location = location;
    f.locationTimestamp = new Date();
    f.status = 'fulfilled';
    f.logs.push({
        timestamp: new Date(),
        message: 'Request notification received'
    })

    await docRef.set(f);
    
    return f;
}