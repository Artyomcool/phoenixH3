#pragma once

#include "jni.h"

extern "C" bool cp_utf8_is(jclass, jshort, const char*);
extern "C" jint cp_int(jclass, jshort);
extern "C" void DBG(const char* format, ...);

namespace annotations {
typedef unsigned char  u8;
typedef unsigned short u16;
typedef unsigned int   usize;

struct ByteReader {
    const u8* p; usize n; usize i;
    u8  u1(){ return p[i++]; }
    u16 u2(){ u16 v=(p[i]<<8)|p[i+1]; i+=2; return v; }
};

template<typename T>
struct Array {
    u16 count;
    T*  items;

    Array(): count(0), items(0) {}
    explicit Array(u16 c): count(c), items(c ? new T[c] : 0) {}

    Array(const Array& o): count(o.count), items(o.count ? new T[o.count] : 0) {
        for(u16 i=0;i<count;i++) items[i] = o.items[i];
    }
    Array& operator=(const Array& o){
        if(this!=&o){
            delete[] items;
            count = o.count;
            items = count ? new T[count] : 0;
            for(u16 i=0;i<count;i++) items[i] = o.items[i];
        }
        return *this;
    }

    ~Array(){ delete[] items; }

    T& operator[](u16 i)       { return items[i]; }
    const T& operator[](u16 i) const { return items[i]; }
};

struct Pair;

struct Annotation {
    u16        type_index;
    Array<Pair> pairs;           // element_value_pairs
};

struct AnnotationValue {
    u8         tag;              // 'B','C','D','F','I','J','S','Z','s','c','@','['
    u16        const_index;      // const_value_index / class_info_index / ('@' — type_index)
    Array<Pair> pairs;           // '@' / '['
};

struct Pair {
    u16            name_index;
    AnnotationValue value;
};

AnnotationValue parseAnnotationValue(ByteReader& r);

Array<Pair> parsePairArray(ByteReader& r, bool read_name){
    u16 n = r.u2();
    Array<Pair> arr(n);
    for(u16 i=0;i<n;i++){
        if (read_name) arr[i].name_index = r.u2();
        arr[i].value = parseAnnotationValue(r);
    }
    return arr;
}

Annotation parseAnnotation(ByteReader& r){
    Annotation a;
    a.type_index = r.u2();
    a.pairs = parsePairArray(r, true);
    return a;
}

Array<Annotation> parseAnnotationArray(ByteReader& r){
    u16 n = r.u2();
    Array<Annotation> arr(n);
    for(u16 i=0;i<n;i++) arr[i] = parseAnnotation(r);
    return arr;
}

AnnotationValue parseAnnotationValue(ByteReader& r){
    AnnotationValue v; v.tag=0; v.const_index=0;
    v.tag = r.u1();
    switch(v.tag){
        case 'B': case 'C': case 'D': case 'F':
        case 'I': case 'J': case 'S': case 'Z':
        case 's':
        case 'c':
            v.const_index = r.u2();
            break;
        case '@':
            v.const_index = r.u2();        // nested type_index
            v.pairs = parsePairArray(r, true);
            break;
        case '[': 
            v.pairs = parsePairArray(r, false);
            break;
    }
    return v;
}

// Runtime[In]VisibleAnnotations → Array<Annotation>
Array<Annotation> parseMethodAnnotations(const u8* data, usize len){
    ByteReader r{data,len,0};
    return parseAnnotationArray(r);
}

// Runtime[In]VisibleParameterAnnotations → Array< Array<Annotation> >
Array< Array<Annotation> > parseParameterAnnotations(const u8* data, usize len){
    ByteReader r{data,len,0};
    u16 pc = r.u1();
    Array< Array<Annotation> > params(pc);
    for(u16 p=0;p<pc;p++) params[p] = parseAnnotationArray(r);
    return params;
}

enum ParamAnnKind { PA_None, PA_Text, PA_R, PA_Arg, PA_Dword };
enum CallConv { CC_Stdcall = 0, CC_VTable = 1, CC_Cdecl = 2, CC_Thiscall = 3, CC_Fastcall = 4 };

struct ParamAnn {
    ParamAnnKind kind;
    int at, offset, length;
    ParamAnn() : kind(PA_None), at(0), offset(0), length(0) {}
};

struct UpcallAnn {
    int base = 0;
    int offset = 0;
};

struct DowncallAnn {
    int value = 0;
    CallConv cc = CC_Stdcall;
};

ParamAnnKind kind_from_type(jclass clazz, u16 type_idx) {
    if (cp_utf8_is(clazz, type_idx, "Lphoenix/h3/annotations/Text;"))  return PA_Text;
    if (cp_utf8_is(clazz, type_idx, "Lphoenix/h3/annotations/Dword;")) return PA_Dword;
    if (cp_utf8_is(clazz, type_idx, "Lphoenix/h3/annotations/R;"))     return PA_R;
    if (cp_utf8_is(clazz, type_idx, "Lphoenix/h3/annotations/Arg;"))   return PA_Arg;
    return PA_None;
}

bool parseUpcallAnnotation(jclass clazz, const u8* data, usize len, UpcallAnn& out) {
    Array<Annotation> anns = parseMethodAnnotations(data, len);
    for (u16 i = 0; i < anns.count; ++i) {
        const Annotation& a = anns.items[i];
        if (!cp_utf8_is(clazz, a.type_index, "Lphoenix/h3/annotations/Upcall;")) continue;

        for (u16 j = 0; j < a.pairs.count; ++j) {
            const Pair& p = a.pairs.items[j];
            const u16 vi = p.value.const_index;
            if (cp_utf8_is(clazz, p.name_index, "base"))   out.base   = cp_int(clazz, vi);
            else if (cp_utf8_is(clazz, p.name_index, "offset")) out.offset = cp_int(clazz, vi);
        }
        return true;
    }
    return false;
}

Array<ParamAnn> parseParamAnnArray(jclass clazz, const u8* data, usize len) {
    Array< Array<Annotation> > params = parseParameterAnnotations(data, len);
    Array<ParamAnn> result(params.count);

    for (u16 p = 0; p < params.count; ++p) {
        const Array<Annotation>& anns = params[p];

        ParamAnn pa;
        for (u16 i = 0; i < anns.count; ++i) {
            const Annotation& a = anns.items[i];

            ParamAnnKind k =
                cp_utf8_is(clazz, a.type_index, "Lphoenix/h3/annotations/Text;")   ? PA_Text   :
                cp_utf8_is(clazz, a.type_index, "Lphoenix/h3/annotations/Dword;")  ? PA_Dword  :
                cp_utf8_is(clazz, a.type_index, "Lphoenix/h3/annotations/R;")      ? PA_R      :
                cp_utf8_is(clazz, a.type_index, "Lphoenix/h3/annotations/Arg;")    ? PA_Arg    :
                                                                                     PA_None;
            if (k == PA_None) continue;

            pa.kind = k;
            for (u16 j = 0; j < a.pairs.count; ++j) {
                const Pair& pr = a.pairs.items[j];
                const u16 vi = pr.value.const_index;
                if (cp_utf8_is(clazz, pr.name_index, "at") || cp_utf8_is(clazz, pr.name_index, "value")) pa.at = cp_int(clazz, vi);
                else if (cp_utf8_is(clazz, pr.name_index, "offset")) pa.offset = cp_int(clazz, vi);
                else if (cp_utf8_is(clazz, pr.name_index, "length")) pa.length = cp_int(clazz, vi);
            }
            break;
        }

        result[p] = pa;
    }

    return result;
}

bool parseDowncallAnnotation(jclass clazz, const u8* data, usize len, DowncallAnn& out) {
    Array<Annotation> anns = parseMethodAnnotations(data, len);

    bool found = false;
    for (u16 i = 0; i < anns.count; ++i) {
        const Annotation& a = anns.items[i];

        if (cp_utf8_is(clazz, a.type_index, "Lphoenix/h3/annotations/Cdecl;"))    { out.cc = CC_Cdecl;    continue; }
        if (cp_utf8_is(clazz, a.type_index, "Lphoenix/h3/annotations/Thiscall;")) { out.cc = CC_Thiscall; continue; }
        if (cp_utf8_is(clazz, a.type_index, "Lphoenix/h3/annotations/VTable;"))   { out.cc = CC_VTable;   continue; }
        if (cp_utf8_is(clazz, a.type_index, "Lphoenix/h3/annotations/Fastcall;")) { out.cc = CC_Fastcall; continue; }

        if (cp_utf8_is(clazz, a.type_index, "Lphoenix/h3/annotations/Downcall;")) {
            for (u16 j = 0; j < a.pairs.count; ++j) {
                const Pair& p = a.pairs.items[j];
                if (cp_utf8_is(clazz, p.name_index, "value"))
                    out.value = cp_int(clazz, p.value.const_index);
            }
            found = true;
        }
    }
    return found;
}

}