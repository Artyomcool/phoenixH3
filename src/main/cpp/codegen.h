#pragma once

#include "jni.h"
#include "annotations.h"

namespace codegen {

using namespace annotations;

enum Reg { EAX=0, ECX=1, EDX=2, EBX=3, ESP=4, EBP=5, ESI=6, EDI=7 };
struct Mem { Reg base; int disp; }; // [base + disp32]

struct CodeGen {
    char* p; int i; int cap;
    static CodeGen create(int capacity) {
        return { new char[capacity], 0, capacity };
    }

    void require(int add) {
        int need = i + add;
        if (need <= cap) return;
        int nc = cap; while (nc < need) nc <<= 1;
        char* nb = new char[nc];
        for (int i = 0; i < cap; ++i) nb[i] = p[i];
        delete[] p; p = nb; cap = nc;
    }

    void u8(int v)            { require(1);  p[i++] = (char)v; }
    void u32(int v)           { require(4);  *(int*)(p + i) = v; i += 4; }
    void emit_mem(int reg_field, Mem m){
        if (m.base == ESP) { u8(0x84); u8(0x24); }
        else  u8(0x80 | (reg_field<<3) | m.base);
        u32(m.disp);
    }
    void emit_modrm(int mod, int reg, int rm){ u8((mod<<6) | (reg<<3) | rm); }
    
    void push(int imm){ u8(0x68); u32(imm); }
    void push(Reg r){ u8(0x50 + r); }
    void pop(Reg r){ u8(0x58 + r); }
    void mov(Reg r, int imm){ u8(0xB8 + r); u32(imm); }
    void mov(Reg dst, Reg src){ u8(0x8B); emit_modrm(0b11, dst, src); }
    void mov(Reg dst, Mem m){ u8(0x8B); emit_mem(dst, m); }
    void mov(Mem m, Reg src){ u8(0x89); emit_mem(src, m); }
    void add(Reg r, int imm){ u8(0x81); u8(0xC0 + r); u32(imm); }
    void call(Reg r){ u8(0xFF); u8(0xD0 + r); }

    void ret_(){ u8(0xC3); }
    void int3(){ u8(0xCC); }
    void nop(){ u8(0x90); }

};

void* make_generated_function(int target_address, int argc, int type, void* push_downcall) {
    CodeGen c = CodeGen::create(0x20);

    c.push(type);
    c.push(argc);
    c.push(target_address);
    c.mov(EAX, (int)push_downcall);
    c.call(EAX);
    c.ret_();

    return (void*)c.p;
}

inline int  load_u32(const void* a)                 { return *(const int*)a; }
inline void* arg_addr(int regs_end, int at)         { char* b=(char*)regs_end; return b + at * 4 + 36; }
inline void* reg_addr(int regs_end, int at)         { char* b=(char*)regs_end; return b + at * 4; }
inline void* dword_addr(int regs_end, int at, int off) {
    if (at == -1) return (void*)(unsigned int)off;
    unsigned int base = *(unsigned int*)reg_addr(regs_end, at);
    return (void*)(base + (unsigned int)off);
}

extern "C" void bind_entry_int(jobject entry, int i, jint v);
extern "C" void bind_entry_obj(jobject entry, int i, jobject v);
extern "C" void bind_entry_str(jobject entry, int i, const char* ptr, int len);
extern "C" jobject new_entry_activation_api(JNIEnv* jni, jclass clazz, int method_index);
extern "C" JNIEnv _jni_env;

jobject __stdcall create_entry_with_this(jobject v, int method) {
    _jni_env.PushLocalFrame(2);
    
    jclass classHandle = _jni_env.GetObjectClass(v);
    // fixme entry is actually a Raw reference
    // so it is VERY unsafe
    // once GC executes - entry reference will be broken
    jobject entry = new_entry_activation_api(&_jni_env, classHandle, method);
    bind_entry_obj(entry, 0, v);

    return _jni_env.PopLocalFrame(entry);
}

void __stdcall handle_arg_ann(jobject entry, int regs_end, int slot, int at) {
    bind_entry_int(entry, slot, load_u32(arg_addr(regs_end, at)));
}
void __stdcall handle_r_ann(jobject entry, int regs_end, int slot, int at) {
    bind_entry_int(entry, slot, load_u32(reg_addr(regs_end, at)));
}
void __stdcall handle_dword_ann(jobject entry, int regs_end, int slot, int at, int offset) {
    bind_entry_int(entry, slot, load_u32(dword_addr(regs_end, at, offset)));
}

void __stdcall handle_text_ann(jobject entry, int regs_end, int slot, int at, int offset, int length) {
    void* ea = dword_addr(regs_end, at, offset);
    bind_entry_str(entry, slot, (char*)ea, length);
}

typedef jobject (__attribute__((fastcall)) *CreateEntryFunc)(int);

CreateEntryFunc emit_bind_params_fastcall(jobject _this, int method, const Array<ParamAnn>& params)
{
    CodeGen c = CodeGen::create(0x40);
    bool hasParams = params.count > 0;
    
    if (hasParams) {
        //c.int3();
        c.push(EBX);
        c.push(EDI);
        c.mov(EBX, ECX);      // EBX/ECX = regs_end
    }

    c.push(method);
    c.push((int)(void*)_this);
    c.mov(EAX, (int)(void*)&create_entry_with_this);
    c.call(EAX);
    if (!hasParams) {
        c.ret_();
        return (CreateEntryFunc)c.p;
    }
    c.mov(EDI, EAX);    // EDI = entry

    int slot = 1;   // 0 = this

    for (u16 k = 0; k < params.count; ++k) {
        const ParamAnn& a = params.items[k];

        void* call = (void*)0xFAADBAAD;

        switch (a.kind) {
            case PA_Arg: call = (void*)&handle_arg_ann; break;
            case PA_R: call = (void*)&handle_r_ann; break;
            case PA_Dword:
                c.push(a.offset);
                call = (void*)&handle_dword_ann;
                break;
            case PA_Text:
                c.push(a.length);
                c.push(a.offset);
                call = (void*)&handle_text_ann; // always object
                break;
        }
        // all annotations have "at" (value for arg/r)
        c.push(a.at);

        // Common call frame: (entry, regs_end, slot, ...kind-specific...)
        c.push(slot++);
        c.push(EBX);        // regs_end
        c.push(EDI);        // entry
        c.mov(EAX, (int)call);
        c.call(EAX);        // __stdcall handlers clean their own stack
    }

    c.mov(EAX, EDI);
    c.pop(EDI);
    c.pop(EBX);
    c.ret_();

    return (CreateEntryFunc)c.p;
}


}