package com.sfc.rpc.support;

import com.sfc.rpc.RPCRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class RPCContext {
    private RPCRequest request;

    private Boolean isIgnore;
}
